package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.SleepingState;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;
import ir.ac.kntu.concurrenttransmission.events.SimNewRoundEvent;
import ir.ac.kntu.distributedsystems.paxos.wmultipaxos.WirelessMultiPaxos;
import ir.ac.kntu.metrics.FailureReason;
import ir.ac.kntu.metrics.FaultModel;
import ir.ac.kntu.metrics.FaultModelProvider;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chaos application that implements dynamic round completion detection.
 * Rounds complete when all nodes reach SleepingState or when timeout (256
 * slots) is reached.
 */
public class ChaosApplication extends AbstractConcurrentTransmissionApplication<ChaosNodeListener>
        implements CtChaosApplication, MetricsEmitter, FaultModelProvider {

    private static final Logger logger = Logger.getLogger(ChaosApplication.class.getSimpleName());

    /**
     * The minimum signal difference (in dB) required for the strongest signal
     * to be successfully captured over the interference from other signals.
     * A common value from literature is 3 dB.
     */
    private static final double CAPTURE_THRESHOLD_DB = 0;
    protected final ChaosStrategies strategies;
    private final ChaosSettings settings;
    private final ChaosStateLogger stateLogger;
    private final SignalModel signalModel;
    private final NodeState startingPoint;
    private final ChaosRoundLifecycle roundLifecycle;
    private final ChaosScenarioRecorder scenarioRecorder;
    private boolean roundActive;
    private int roundsCompleted;
    private MetricsCollector metricsCollector;
    private FaultModel faultModel;
    private long nextSlotTickTime = Long.MIN_VALUE;

    public ChaosApplication(ChaosSettings settings, ChaosStrategies strategies, NetGraph netGraph,
                            NodeState startingPoint) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.strategies = strategies;
        this.stateLogger = new ChaosStateLogger(new ArrayList<>(netGraph.getNodes()), strategies.transmissionPolicy());
        this.signalModel = new SignalModel();
        this.startingPoint = Objects.requireNonNullElseGet(startingPoint,
                () -> strategies.transmissionPolicy().getInitialState());
        this.roundLifecycle = new ChaosRoundLifecycle(strategies.transmissionPolicy());
        this.scenarioRecorder = new ChaosScenarioRecorder();
        this.roundActive = false;
        this.roundsCompleted = 0;
    }

    @Override
    public void setListener(CtNode node, ChaosNodeListener listener) {
        super.setListener(node, listener);
    }

    /**
     * Called when a node's state changes to track completion conditions
     */
    public void onNodeStateChanged(StatefulNode node, NodeState oldState, NodeState newState) {
        roundLifecycle.onNodeStateChanged(oldState, newState);
    }

    public ChaosStateLogger getStateLogger() {
        return this.stateLogger;
    }

    @Override
    public void simulationStarting(ContextView context) {
        newRound(context);
    }

    @Override
    public void simulationFinishing(ContextView context) {
        if (!roundLifecycle.isRoundCompleted())
            strategies.transmissionPolicy().endRound(context.getTime() + 1);
    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        final ChaosTransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        updateNetworkTime(transmissionPolicy.getNetworkTime(context.getTime()));

        if (!roundActive) {
            return;
        }

        recordKnowledgeSnapshot(context);

        for (CtNode node : context.getNetGraph().getNodes()) {
            if (node instanceof StatefulNode statefulNode) {
                statefulNode.beginSlot(context);
                NodeState state = statefulNode.getCurrentState();
                if (state != null) {
                    state.onSlotStart(statefulNode, context);
                }
            }
        }

        // Check for timeout
        if (roundLifecycle.shouldTimeout(context.getTime())) {
            logger.log(Level.INFO,
                    "Round timeout reached at slot " + roundLifecycle.elapsedSlots(context.getTime()));
            completeRound(context);
        }
    }

    @Override
    public void newRound(ContextView context) {
        Objects.requireNonNull(context);
        if (roundActive) {
            logger.log(Level.FINE, "Round already active; ignoring additional newRound call.");
            return;
        }

        updateNetworkTime(strategies.transmissionPolicy().getNetworkTime(context.getTime()));
        if (roundsCompleted >= settings.roundLimit()) {
            logger.log(Level.INFO, "All rounds completed. Simulation finished.");
            return;
        }
        roundActive = true;
        nextSlotTickTime = Long.MIN_VALUE;

        int sleepingNodes = (int) context.getNetGraph().getNodes().stream()
                .filter(node -> node instanceof StatefulNode statefulNode
                        && statefulNode.getCurrentState() instanceof SleepingState)
                .count();
        roundLifecycle.reset(context.getTime(), sleepingNodes);

        int roundIndex = roundsCompleted + 1;
        logger.log(Level.INFO, "Starting round " + roundIndex + " at time " + context.getTime());

        final int nextInitiatorId = strategies.initiatorStrategy().getNextInitiatorId();
        context.getNetGraph().getNodes().forEach(node -> {
            if (node instanceof StatefulNode statefulNode) {
                CtMessage<ChaosMessage> initialMessage = getChaosNodeListener(node)
                        .initiateMessage(context, node, context.getNetGraph().getNodeById(nextInitiatorId));
                if (initialMessage == null) {
                    throw new IllegalStateException(
                            "No initial knowledge available for node " + node.getId());
                }
                statefulNode.initializeForNewRound(context, initialMessage, this.stateLogger,
                        startingPoint);
            }
        });

        recordKnowledgeSnapshot(context);

        SimInitiateFloodEvent initiateFloodEvent = new SimInitiateFloodEvent(context.getTime(), nextInitiatorId);
        context.getSimulator().scheduleEvent(initiateFloodEvent);
        scheduleSlotTick(context, context.getTime() + 1);
    }

    public void checkRoundCompletion(ContextView context) {
        if (!roundActive || roundLifecycle.isRoundCompleted())
            return;

        // Log current states of all nodes for debugging
        StringBuilder stateLog = new StringBuilder("Node states: ");
        context.getNetGraph().getNodes().stream()
                .filter(node -> node instanceof StatefulNode)
                .forEach(node -> {
                    StatefulNode statefulNode = (StatefulNode) node;
                    stateLog.append(String.format("Node[%d]=%s ", node.getId(), statefulNode.getCurrentState()));
                });
        logger.log(Level.FINE, stateLog.toString());

        int totalNodes = listenersView().size();

        if (roundLifecycle.shouldCompleteBySleeping(totalNodes)) {
            completeRound(context);
        }
    }

    private void completeRound(ContextView context) {
        if (!roundActive || roundLifecycle.isRoundCompleted())
            return;

        roundLifecycle.markCompleted();
        long actualSlotsUsed = roundLifecycle.elapsedSlots(context.getTime());
        logger.log(Level.INFO, "Round completed in " + actualSlotsUsed + " slots");
        strategies.transmissionPolicy().endRound(context.getTime() + 1);
        roundActive = false;
        roundsCompleted++;
        nextSlotTickTime = Long.MIN_VALUE;

        if (roundsCompleted < settings.roundLimit()) {
            context.getSimulator().scheduleEvent(new SimNewRoundEvent(context.getTime() + 1));
        } else {
            logger.log(Level.INFO, "All rounds completed. Simulation finished.");
        }
    }

    private void scheduleSlotTick(ContextView context, long time) {
        if (!roundActive) {
            return;
        }
        if (time <= nextSlotTickTime) {
            return;
        }
        nextSlotTickTime = time;
        context.getSimulator().scheduleEvent(
                Event.create("ChaosSlotTick", time, SimEventPriority.Low, (ctx) -> {
                    if (!roundActive) {
                        return;
                    }
                    scheduleSlotTick(ctx, ctx.getTime() + 1);
                }));
    }

    @Override
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final CtNode initiator = getInitiatorNode(context);
        if (metricsCollector != null) {
            ChaosNodeListener listener = getChaosNodeListener(initiator);
            boolean skipDecisionStart = listener instanceof WirelessMultiPaxos;
            if (!skipDecisionStart) {
                metricsCollector.recordDecisionStart(getRound(), initiator.getId(), context.getTime());
            }
        }
        initiator.initiateFlood(context, initiator);
    }

    @Override
    public void ctPacketsReceived(CtPacketsEvent ctEvent, ContextView context) {
        Objects.requireNonNull(ctEvent);
        Objects.requireNonNull(context);

        final List<FloodPacket<?>> packets = ctEvent.getPackets();

        if (packets.isEmpty())
            return;

        final CtNode receiver = ctEvent.getReceiver();
        if (!(receiver instanceof StatefulNode))
            return; // Only process for stateful nodes
        StatefulNode statefulReceiver = (StatefulNode) receiver;
        CtNetworkTime currentTime = getNetworkTime();
        if (statefulReceiver.getCurrentState() != null && !statefulReceiver.getCurrentState().isListening()) {
            if (currentTime != null) {
                packets.forEach(packet -> {
                    CtNetworkTime failureTime = determineEventTime(packet, currentTime);
                    scenarioRecorder.recordEvent(failureTime,
                            ChaosScenarioRecorder.TransmissionEvent.failure("flood", packet,
                                    FailureReason.NOT_LISTENING));
                });
            }
            if (metricsCollector != null) {
                int round = getRound();
                for (int i = 0; i < packets.size(); i++) {
                    metricsCollector.recordReceiveFailure(round, receiver.getId(), FailureReason.NOT_LISTENING);
                }
            }
            return;
        }

        FloodPacket<?> capturedPacket = selectPacketBySignal(packets, statefulReceiver, context);

        if (capturedPacket != null) {
            boolean dropped = faultModel != null
                    ? faultModel.shouldDropPacket()
                    : Math.random() < settings.lossProbability();
            CtNetworkTime successTime = null;
            if (currentTime != null) {
                successTime = determineEventTime(capturedPacket, currentTime);
                if (!dropped) {
                    scenarioRecorder.recordEvent(successTime,
                            ChaosScenarioRecorder.TransmissionEvent.success("flood", capturedPacket));
                } else {
                    scenarioRecorder.recordEvent(successTime,
                            ChaosScenarioRecorder.TransmissionEvent.failure("flood", capturedPacket,
                                    FailureReason.DROP));
                }
                packets.stream()
                        .filter(packet -> packet != capturedPacket)
                        .forEach(packet -> {
                            CtNetworkTime failureTime = determineEventTime(packet, currentTime);
                            scenarioRecorder.recordEvent(failureTime,
                                    ChaosScenarioRecorder.TransmissionEvent.failure("flood", packet,
                                            FailureReason.COLLISION));
                        });
            }
        if (!dropped) {
            if (metricsCollector != null) {
                int round = getRound();
                metricsCollector.recordReceiveSuccess(round, receiver.getId());
                for (FloodPacket<?> packet : packets) {
                    if (packet != capturedPacket) {
                            metricsCollector.recordReceiveFailure(round, receiver.getId(), FailureReason.COLLISION);
                        }
                    }
                }
                statefulReceiver.handlePacket(context, capturedPacket);
                if (successTime == null) {
                    successTime = getNetworkTime();
                }
                if (successTime != null) {
                    recordKnowledge(successTime, statefulReceiver);
                }
                checkRoundCompletion(context);
            } else {
                if (metricsCollector != null) {
                    int round = getRound();
                    metricsCollector.recordReceiveFailure(round, receiver.getId(), FailureReason.DROP);
                    for (FloodPacket<?> packet : packets) {
                        if (packet != capturedPacket) {
                            metricsCollector.recordReceiveFailure(round, receiver.getId(), FailureReason.COLLISION);
                        }
                    }
                }
                getChaosNodeListener(receiver).ctPacketsLost(context, packets, false);
            }
        } else {
            if (currentTime != null) {
                packets.forEach(packet -> {
                    CtNetworkTime failureTime = determineEventTime(packet, currentTime);
                    scenarioRecorder.recordEvent(failureTime,
                            ChaosScenarioRecorder.TransmissionEvent.failure("flood", packet, FailureReason.COLLISION));
                });
            }
            if (metricsCollector != null) {
                int round = getRound();
                for (int i = 0; i < packets.size(); i++) {
                    metricsCollector.recordReceiveFailure(round, receiver.getId(), FailureReason.COLLISION);
                }
            }
            getChaosNodeListener(receiver).ctPacketsLost(context, packets, false);
        }
    }

    private FloodPacket<?> selectPacketBySignal(List<FloodPacket<?>> packets, StatefulNode receiver,
                                                ContextView context) {
        if (packets.size() == 1) {
            return packets.get(0);
        }

        // Stabilize ordering for deterministic fading with seeded RNG.
        List<FloodPacket<?>> orderedPackets = new ArrayList<>(packets);
        orderedPackets.sort(Comparator.comparingInt(packet -> packet.sender() == null ? -1 : packet.sender().getId()));

        FloodPacket<?> strongestPacket = null;
        double maxSignalStrengthDb = -Double.MAX_VALUE;
        double totalInterferencePowerMw = 0;

        for (FloodPacket<?> packet : orderedPackets) {
            double distance = context.getNetGraph().getDistanceBetween(packet.sender(), receiver);
            double signalStrengthDb = signalModel.calculateSignalStrengthDb(distance);

            if (signalStrengthDb > maxSignalStrengthDb) {
                if (strongestPacket != null) {
                    totalInterferencePowerMw += signalModel.dbmToMilliwatts(maxSignalStrengthDb);
                }
                maxSignalStrengthDb = signalStrengthDb;
                strongestPacket = packet;
            } else {
                totalInterferencePowerMw += signalModel.dbmToMilliwatts(signalStrengthDb);
            }
        }

        if (totalInterferencePowerMw <= 0) {
            return strongestPacket;
        }

        double signalToInterferenceRatioDb = 10
                * Math.log10(signalModel.dbmToMilliwatts(maxSignalStrengthDb) / totalInterferencePowerMw);

        if (signalToInterferenceRatioDb >= CAPTURE_THRESHOLD_DB) {
            return strongestPacket;
        } else {
            logger.warning(String.format("[t:%d] Capture FAILED at Node[%d].", context.getTime(), receiver.getId()));
            return null;
        }
    }

    @Override
    public CtNode getInitiatorNode(ContextView context) {
        return context.getNetGraph().getNodeById(strategies.initiatorStrategy().getCurrentInitiatorId());
    }

    @Override
    public ChaosTransmissionPolicy getTransmissionPolicy() {
        return strategies.transmissionPolicy();
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return getChaosNodeListener(sender).getMessage(context, sender, receivedMessage, whichRepeat);
    }

    /**
     * Retrieves the specific listener for a given node.
     * The listener contains the application-specific logic (e.g., how to merge
     * messages).
     *
     * @param node The node for which to get the listener.
     * @return The ChaosNodeListener associated with the node.
     * @throws IllegalStateException if no listener is defined for the node.
     */
    @Override
    public ChaosNodeListener getChaosNodeListener(CtNode node) {
        return getListener(node);
    }

    public Logger getLogger() {
        return logger;
    }

    public int getRound() {
        CtNetworkTime networkTime = getNetworkTime();
        return networkTime != null ? networkTime.round() : 0;
    }

    public int getSlot() {
        CtNetworkTime networkTime = getNetworkTime();
        return networkTime != null ? networkTime.slot() : 0;
    }

    public boolean isRoundOpen() {
        return roundActive && !roundLifecycle.isRoundCompleted();
    }

    public ChaosScenarioRecorder getScenarioRecorder() {
        return scenarioRecorder;
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    public void setMetricsCollector(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public FaultModel getFaultModel() {
        return faultModel;
    }

    public void setFaultModel(FaultModel faultModel) {
        this.faultModel = faultModel;
        if (faultModel != null) {
            signalModel.reseed(faultModel.seed() ^ 0xD1B54A32D192ED03L);
        }
        ConcurrentTransmissionPolicy policy = strategies.transmissionPolicy();
        if (policy instanceof FaultAwareTransmissionPolicy aware) {
            aware.setFaultModel(faultModel);
        }
    }


    public void configureScenarioMetadata(String name, String author, String description) {
        scenarioRecorder.setScenarioMetadata(name, author, description);
    }

    public void configureRoundMetadata(int roundIndex, String label, String description) {
        scenarioRecorder.setRoundMetadata(roundIndex, label, description);
    }

    public Optional<Path> exportScenarioReport(NetGraph netGraph) {
        Objects.requireNonNull(netGraph);
        try {
            Path outputPath = prepareReportPath();
            writeScenarioReport(netGraph, outputPath);
            logger.log(Level.INFO, "Scenario report exported to {0}", outputPath.toAbsolutePath());
            return Optional.of(outputPath);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to export scenario report", e);
            return Optional.empty();
        }
    }

    private Path prepareReportPath() throws Exception {
        ChaosScenarioRecorder.ScenarioMetadata metadata = scenarioRecorder.getMetadata();
        String slug = slugify(metadata.name());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path directory = Path.of("logs");
        Files.createDirectories(directory);
        return directory.resolve(slug + "_" + timestamp + ".yaml");
    }

    private void writeScenarioReport(NetGraph netGraph, Path outputPath) throws Exception {
        YamlBuilder builder = new YamlBuilder();
        builder.appendLine("id: " + quote(UUID.randomUUID().toString()));
        ChaosScenarioRecorder.ScenarioMetadata metadata = scenarioRecorder.getMetadata();
        builder.appendLine("name: " + quote(metadata.name()));

        builder.appendLine("metadata:");
        builder.increaseIndent();
        builder.appendLine("author: " + quote(metadata.author()));
        builder.appendLine("description: " + quote(metadata.description()));
        builder.decreaseIndent();

        List<CtNode> sortedNodes = new ArrayList<>(netGraph.getNodes());
        sortedNodes.sort(Comparator.comparingInt(CtNode::getId));

        builder.appendLine("nodes:");
        builder.increaseIndent();
        for (int index = 0; index < sortedNodes.size(); index++) {
            CtNode node = sortedNodes.get(index);
            builder.startListItem("id: " + node.getId());
            builder.appendLine("label: " + quote(generateNodeLabel(index)));
            Point2D.Double coordinates = netGraph.getCoordinates(node);
            if (coordinates != null) {
                builder.appendLine("position: { x: " + formatCoordinate(coordinates.x)
                        + ", y: " + formatCoordinate(coordinates.y) + " }");
            } else {
                builder.appendLine("position: { x: 0, y: 0 }");
            }
            builder.endListItem();
        }
        builder.decreaseIndent();

        builder.appendLine("links:");
        builder.increaseIndent();
        for (CtNode node : sortedNodes) {
            for (CtNode neighbor : netGraph.getNodeNeighbors(node)) {
                if (node.getId() < neighbor.getId()) {
                    builder.startListItem("source: " + node.getId());
                    builder.appendLine("target: " + neighbor.getId());
                    builder.endListItem();
                }
            }
        }
        builder.decreaseIndent();

        SortedMap<CtNetworkTime, Map<CtNode, String>> rawHistory = stateLogger.snapshotHistory();
        SortedMap<CtNetworkTime, Map<Integer, String>> history = new TreeMap<>();
        rawHistory.forEach((time, nodeStateMap) -> {
            Map<Integer, String> byId = new HashMap<>();
            nodeStateMap.forEach((node, state) -> byId.put(node.getId(), state));
            history.put(time, byId);
        });

        SortedMap<CtNetworkTime, Map<CtNode, String>> rawKnowledgeHistory = stateLogger.snapshotKnowledgeHistory();
        SortedMap<CtNetworkTime, Map<Integer, String>> knowledgeHistory = new TreeMap<>();
        rawKnowledgeHistory.forEach((time, knowledgeMap) -> {
            Map<Integer, String> byId = new HashMap<>();
            knowledgeMap.forEach((node, knowledge) -> byId.put(node.getId(), knowledge));
            knowledgeHistory.put(time, byId);
        });

        SortedMap<CtNetworkTime, List<ChaosScenarioRecorder.TransmissionEvent>> events = scenarioRecorder
                .snapshotEvents();

        SortedSet<Integer> roundsPresent = new TreeSet<>();
        history.keySet().forEach(time -> roundsPresent.add(time.round()));
        events.keySet().forEach(time -> roundsPresent.add(time.round()));
        knowledgeHistory.keySet().forEach(time -> roundsPresent.add(time.round()));

        int policyRounds = strategies.transmissionPolicy().getTotalRounds();
        for (int r = 0; r < policyRounds; r++) {
            roundsPresent.add(r);
        }

        builder.appendLine("rounds:");
        builder.increaseIndent();
        for (Integer roundIndex : roundsPresent) {
            int slotCount = determineSlotCount(roundIndex, history, knowledgeHistory, events);
            if (slotCount <= 0) {
                continue;
            }

            ChaosScenarioRecorder.RoundInfo roundInfo = scenarioRecorder.resolveRoundInfo(roundIndex);

            builder.startListItem("id: " + (roundIndex + 1));
            builder.appendLine("label: " + quote(roundInfo.label()));
            builder.appendLine("description: " + quote(roundInfo.description()));
            builder.appendLine("slots:");
            builder.increaseIndent();
            Map<Integer, String> lastStates = new HashMap<>();
            Map<Integer, String> lastKnowledge = new HashMap<>();

            for (int slot = 0; slot < slotCount; slot++) {
                builder.startListItem("id: " + (slot + 1));

                CtNetworkTime timeKey = new CtNetworkTime(roundIndex, slot);
                Map<Integer, String> updates = history.getOrDefault(timeKey, Collections.emptyMap());
                if (!updates.isEmpty()) {
                    updates.forEach(lastStates::put);
                }

                Map<Integer, String> knowledgeUpdates = knowledgeHistory.getOrDefault(timeKey, Collections.emptyMap());
                if (!knowledgeUpdates.isEmpty()) {
                    knowledgeUpdates.forEach(lastKnowledge::put);
                }

                builder.appendLine("nodeStates:");
                builder.increaseIndent();
                for (CtNode node : sortedNodes) {
                    builder.appendLine(node.getId() + ": "
                            + quote(lastStates.getOrDefault(node.getId(), "unknown")));
                }
                builder.decreaseIndent();

                builder.appendLine("knowledge:");
                builder.increaseIndent();
                for (CtNode node : sortedNodes) {
                    builder.appendLine(node.getId() + ": "
                            + quoteNullable(lastKnowledge.get(node.getId())));
                }
                builder.decreaseIndent();

                List<ChaosScenarioRecorder.TransmissionEvent> slotEvents = events.get(timeKey);
                if (slotEvents != null && !slotEvents.isEmpty()) {
                    builder.appendLine("events:");
                    builder.increaseIndent();
                    for (ChaosScenarioRecorder.TransmissionEvent event : slotEvents) {
                        builder.startListItem("type: " + quote(event.type()));
                        builder.appendLine("from: " + event.from());
                        builder.appendLine("to: " + event.to());
                        builder.appendLine("success: " + event.success());
                        if (event.packet() != null) {
                            builder.appendLine("packet:");
                            builder.increaseIndent();
                            builder.appendLine("time: " + event.packet().time());
                            builder.appendLine("initiatorId: " + formatNullable(event.packet().initiatorId()));
                            builder.appendLine("messageNo: " + formatNullable(event.packet().messageNo()));
                            builder.appendLine("content: " + quoteNullable(event.packet().content()));
                            builder.decreaseIndent();
                        }
                        builder.endListItem();
                    }
                    builder.decreaseIndent();
                }

                builder.endListItem();
            }

            builder.decreaseIndent();
            builder.endListItem();
        }
        builder.decreaseIndent();

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(builder.toString());
        }
    }

    private int determineSlotCount(int roundIndex,
                                   SortedMap<CtNetworkTime, Map<Integer, String>> history,
                                   SortedMap<CtNetworkTime, Map<Integer, String>> knowledgeHistory,
                                   SortedMap<CtNetworkTime, List<ChaosScenarioRecorder.TransmissionEvent>> events) {
        int maxSlot = -1;
        for (CtNetworkTime time : history.keySet()) {
            if (time.round() == roundIndex) {
                maxSlot = Math.max(maxSlot, time.slot());
            }
        }
        for (CtNetworkTime time : knowledgeHistory.keySet()) {
            if (time.round() == roundIndex) {
                maxSlot = Math.max(maxSlot, time.slot());
            }
        }
        for (CtNetworkTime time : events.keySet()) {
            if (time.round() == roundIndex) {
                maxSlot = Math.max(maxSlot, time.slot());
            }
        }

        long duration = strategies.transmissionPolicy().getRoundDuration(roundIndex);
        int computed = duration > 0 ? (int) duration : 0;
        if (maxSlot >= 0) {
            computed = Math.max(computed, maxSlot + 1);
        }
        return computed;
    }

    private static String generateNodeLabel(int index) {
        StringBuilder builder = new StringBuilder();
        int value = index;
        do {
            int remainder = value % 26;
            builder.insert(0, (char) ('A' + remainder));
            value = (value / 26) - 1;
        } while (value >= 0);
        return builder.toString();
    }

    private static String formatCoordinate(double value) {
        String formatted = String.format(Locale.US, "%.2f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String quoteNullable(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String formatNullable(Object value) {
        return value == null ? "null" : value.toString();
    }

    private static String slugify(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized.isEmpty() ? "scenario" : normalized;
    }

    private CtNetworkTime determineEventTime(FloodPacket<?> packet, CtNetworkTime fallback) {
        if (packet == null) {
            return fallback;
        }
        long sendTime = Math.max(0, packet.time());
        return strategies.transmissionPolicy().getNetworkTime(sendTime);
    }

    private void recordKnowledgeSnapshot(ContextView context) {
        CtNetworkTime netTime = getNetworkTime();
        if (netTime == null) {
            return;
        }
        for (CtNode node : context.getNetGraph().getNodes()) {
            if (node instanceof StatefulNode statefulNode) {
                recordKnowledge(netTime, statefulNode);
            }
        }
    }

    private void recordKnowledge(CtNetworkTime time, StatefulNode node) {
        if (time == null || node == null) {
            return;
        }
        stateLogger.setKnowledge(time, node, serializeKnowledge(node.getKnowledge()));
    }

    private static String serializeKnowledge(CtMessage<ChaosMessage> knowledge) {
        if (knowledge == null || knowledge.isNull()) {
            return null;
        }
        return knowledge.toString();
    }

    private static final class YamlBuilder {
        private final StringBuilder content = new StringBuilder();
        private int indent = 0;

        void appendLine(String line) {
            content.append("  ".repeat(Math.max(0, indent))).append(line).append('\n');
        }

        void startListItem(String line) {
            appendLine("- " + line);
            indent++;
        }

        void endListItem() {
            indent = Math.max(0, indent - 1);
        }

        void increaseIndent() {
            indent++;
        }

        void decreaseIndent() {
            indent = Math.max(0, indent - 1);
        }

        @Override
        public String toString() {
            return content.toString();
        }
    }

}
