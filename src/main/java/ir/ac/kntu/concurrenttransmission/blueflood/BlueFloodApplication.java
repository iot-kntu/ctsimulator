package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;
import ir.ac.kntu.concurrenttransmission.events.SimNewRoundEvent;
import ir.ac.kntu.metrics.FailureReason;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlueFloodApplication extends AbstractConcurrentTransmissionApplication<BlueFloodNodeListener>
        implements CtBlueFloodApplication, MetricsEmitter {

    public static double DEFAULT_INTERFERENCE_PROB = 0.9;
    protected final BlueFloodStrategies strategies;
    private final Logger logger = Logger.getLogger("BlueFloodApplication");
    private final BlueFloodSettings settings;
    private Random random = new Random(new Date().getTime());
    private StateLogger stateLogger;
    private final BlueFloodScenarioRecorder scenarioRecorder = new BlueFloodScenarioRecorder();
    private MetricsCollector metricsCollector;

    public BlueFloodApplication(BlueFloodSettings settings, BlueFloodStrategies strategies) {
        this.settings = settings;
        this.strategies = strategies;
    }

    @Override
    public void setListener(CtNode node, BlueFloodNodeListener listener) {
        super.setListener(node, listener);
    }

    @Override
    public void simulationStarting(ContextView context) {
        this.stateLogger = new StateLogger(new ArrayList<>(context.getNetGraph().getNodes()),
                strategies.transmissionPolicy());
        updateNetworkTime(strategies.transmissionPolicy().getNetworkTime(context.getTime()));
        newRound(context);
        recordStateSnapshot(context);
    }

    @Override
    public void simulationFinishing(ContextView context) {

    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        final TransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        updateNetworkTime(transmissionPolicy.getNetworkTime(context.getTime()));
        recordStateSnapshot(context);
    }

    @Override
    public void newRound(ContextView context) {
        CtNetworkTime networkTime = resolveNetworkTime(context);
        if (networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if (networkTime.round() < settings.roundLimit()) {
            final int nextInitiator = strategies.initiatorStrategy().getNextInitiatorId();
            final CtNode initiatorNode = context.getNetGraph().getNodeById(nextInitiator);
            strategies.transmissionPolicy().newRound(networkTime, initiatorNode);
            SimInitiateFloodEvent initiateFloodEvent = new SimInitiateFloodEvent(context.getTime(), nextInitiator);
            context.getSimulator().scheduleEvent(initiateFloodEvent);

            // scheduling the next round to keep the simulation working even with faults
            context.getSimulator().scheduleEvent(
                    new SimNewRoundEvent(context.getTime() + strategies.transmissionPolicy().getTotalSlotsOfRound()));
        }
    }

    @Override
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final CtNode inode = getInitiatorNode(context);
        if (metricsCollector != null) {
            metricsCollector.recordDecisionStart(getRound(), inode.getId(), context.getTime());
        }
        // capture the initial node states for this round before any packets move
        recordStateSnapshot(context);

        inode.initiateFlood(context, inode);

        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() + " initiated message");

        strategies.transmissionPolicy().printCurrentNodeStates();
    }

    @Override
    public void ctPacketsReceived(CtPacketsEvent ctEvent, ContextView context) {
        Objects.requireNonNull(ctEvent);
        Objects.requireNonNull(context);

        final List<FloodPacket<?>> packets = ctEvent.getPackets();

        if (packets.isEmpty())
            return;

        // in case of receiving different packets, there is a chance one packet
        // to be received.
        FloodPacket<?> thePacket = packets.size() == 1
                ? packets.get(0)
                : (ctEvent.areMessagesSimilar() ? packets.get(0) : packets.get(random.nextInt(packets.size())));

        final CtNode receiver = ctEvent.getReceiver();

        switch (getNodeState(receiver)) {

            case Sleep, Flood -> {
                CtNetworkTime time = getNetworkTime();
                if (time != null) {
                    for (FloodPacket<?> packet : packets) {
                        scenarioRecorder.recordEvent(time,
                                BlueFloodScenarioRecorder.TransmissionEvent.failure("flood", packet,
                                        FailureReason.NOT_LISTENING));
                    }
                }
                if (metricsCollector != null) {
                    int round = getRound();
                    for (int i = 0; i < packets.size(); i++) {
                        metricsCollector.recordReceiveFailure(round, receiver.getId(),
                                FailureReason.NOT_LISTENING);
                    }
                }
                getBlueFloodListener(receiver).ctPacketsLost(context, packets, ctEvent.areMessagesSimilar());
            }
            case Listen -> {
                double receiveProbability = ctEvent.areMessagesSimilar()
                        ? settings.lossProbability()
                        : settings.conflictProbability();

                if (random.nextDouble() >= receiveProbability) { // no loss

                    getLogger().log(Level.INFO, String.format("[t:%d-r:%d-s:%d] node[%d] received Pkt[%d]",
                            context.getTime(),
                            getRound(),
                            getSlot(),
                            receiver.getId(), thePacket.ctMessage().messageNo()));

                    boolean shouldFlood = getBlueFloodListener(receiver).ctPacketsReceived(context, packets, thePacket,
                            ctEvent.areMessagesSimilar());
                    recordTransmissionEvents(ctEvent.areMessagesSimilar(), true, packets, thePacket, null);
                    if (metricsCollector != null) {
                        int round = getRound();
                        if (ctEvent.areMessagesSimilar()) {
                            for (FloodPacket<?> packet : packets) {
                                metricsCollector.recordReceiveSuccess(round, receiver.getId());
                            }
                        } else {
                            metricsCollector.recordReceiveSuccess(round, receiver.getId());
                            for (FloodPacket<?> packet : packets) {
                                if (packet != thePacket) {
                                    metricsCollector.recordReceiveFailure(round, receiver.getId(),
                                            FailureReason.COLLISION);
                                }
                            }
                        }
                    }
                    if (shouldFlood) {
                        strategies.transmissionPolicy().newPacketReceived(receiver, getSlot());
                        receiver.floodMessage(context, 1, receiver, thePacket.ctMessage());
                    }
                } else {
                    FailureReason reason = ctEvent.areMessagesSimilar() ? FailureReason.DROP : FailureReason.COLLISION;
                    recordTransmissionEvents(ctEvent.areMessagesSimilar(), false, packets, thePacket, reason);
                    if (metricsCollector != null) {
                        int round = getRound();
                        for (int i = 0; i < packets.size(); i++) {
                            metricsCollector.recordReceiveFailure(round, receiver.getId(), reason);
                        }
                    }
                    getBlueFloodListener(receiver).ctPacketsLost(context, packets, ctEvent.areMessagesSimilar());
                }
            }
        }

        // log the updated per-slot plan after state changes
        recordStateSnapshot(context);

        strategies.transmissionPolicy().printCurrentNodeStates();
    }

    @Override
    public CtNode getInitiatorNode(ContextView context) {
        return context.getNetGraph().getNodeById(strategies.initiatorStrategy().getCurrentInitiatorId());
    }

    @Override
    public NodeState getNodeState(CtNode node) {
        return strategies.transmissionPolicy().getNodeState(node, getSlot());
    }

    @Override
    public TransmissionPolicy getTransmissionPolicy() {
        return strategies.transmissionPolicy();
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return getBlueFloodListener(sender).getMessage(context, sender, receivedMessage, whichRepeat);
    }

    @Override
    public CtMessage<?> getRoundInitiationMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return getBlueFloodListener(initiator).initiateMessage(context, initiator, whichRepeat);
    }

    public Logger getLogger() {
        return logger;
    }

    public String printTimeline() {
        return strategies.transmissionPolicy().printHistory();
    }

    public int getRound() {
        CtNetworkTime networkTime = getNetworkTime();
        return networkTime != null ? networkTime.round() : 0;
    }

    public int getSlot() {
        CtNetworkTime networkTime = getNetworkTime();
        return networkTime != null ? networkTime.slot() : 0;
    }

    public StateLogger getStateLogger() {
        return stateLogger;
    }

    public BlueFloodScenarioRecorder getScenarioRecorder() {
        return scenarioRecorder;
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    public void setMetricsCollector(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    public void setRandomSeed(long seed) {
        this.random = new Random(seed);
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

    private BlueFloodNodeListener getBlueFloodListener(CtNode node) {
        return getListener(node);
    }

    private void recordStateSnapshot(ContextView context) {
        if (stateLogger == null) {
            return;
        }
        CtNetworkTime netTime = getNetworkTime();
        if (netTime == null) {
            return;
        }
        int totalSlots = strategies.transmissionPolicy().getTotalSlotsOfRound();
        int roundIndex = netTime.round();
        for (int slot = 0; slot < totalSlots; slot++) {
            CtNetworkTime timeKey = new CtNetworkTime(roundIndex, slot);
            for (CtNode node : context.getNetGraph().getNodes()) {
                stateLogger.setState(timeKey, node,
                        strategies.transmissionPolicy().getNodeState(node, slot));
            }
        }
    }

    private void recordTransmissionEvents(boolean areSimilar, boolean success, List<FloodPacket<?>> packets,
            FloodPacket<?> selected, FailureReason failureReason) {
        CtNetworkTime time = getNetworkTime();
        if (time == null || packets == null || packets.isEmpty()) {
            return;
        }

        if (success && areSimilar) {
            packets.forEach(packet -> scenarioRecorder.recordEvent(time,
                    BlueFloodScenarioRecorder.TransmissionEvent.success("flood", packet)));
            return;
        }

        for (FloodPacket<?> packet : packets) {
            boolean isSuccess = success && Objects.equals(packet, selected);
            scenarioRecorder.recordEvent(time,
                    isSuccess
                            ? BlueFloodScenarioRecorder.TransmissionEvent.success("flood", packet)
                            : BlueFloodScenarioRecorder.TransmissionEvent.failure("flood", packet,
                                    success ? FailureReason.COLLISION : failureReason));
        }
    }

    private Path prepareReportPath() throws Exception {
        BlueFloodScenarioRecorder.ScenarioMetadata metadata = scenarioRecorder.getMetadata();
        String slug = slugify(metadata.name());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path directory = Path.of("results/_runs");
        Files.createDirectories(directory);
        return directory.resolve(slug + "_" + timestamp + ".yaml");
    }

    private void writeScenarioReport(NetGraph netGraph, Path outputPath) throws Exception {
        YamlBuilder builder = new YamlBuilder();
        builder.appendLine("id: " + quote(UUID.randomUUID().toString()));
        builder.appendLine("type: \"blueflood\"");
        BlueFloodScenarioRecorder.ScenarioMetadata metadata = scenarioRecorder.getMetadata();
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

        SortedMap<CtNetworkTime, Map<CtNode, NodeState>> rawHistory = stateLogger != null
                ? stateLogger.snapshotHistory()
                : new TreeMap<>();
        SortedMap<CtNetworkTime, Map<Integer, String>> history = new TreeMap<>();
        rawHistory.forEach((time, nodeStateMap) -> {
            Map<Integer, String> byId = new HashMap<>();
            nodeStateMap.forEach((node, state) -> byId.put(node.getId(), state == null ? null : state.name()));
            history.put(time, byId);
        });

        SortedMap<CtNetworkTime, List<BlueFloodScenarioRecorder.TransmissionEvent>> events = scenarioRecorder
                .snapshotEvents();

        SortedSet<Integer> roundsPresent = new TreeSet<>();
        history.keySet().forEach(time -> roundsPresent.add(time.round()));
        events.keySet().forEach(time -> roundsPresent.add(time.round()));
        for (int r = 0; r < settings.roundLimit(); r++) {
            roundsPresent.add(r);
        }

        builder.appendLine("rounds:");
        builder.increaseIndent();
        for (Integer roundIndex : roundsPresent) {
            int slotCount = determineSlotCount(roundIndex, history, events);
            if (slotCount <= 0) {
                continue;
            }

            BlueFloodScenarioRecorder.RoundInfo roundInfo = scenarioRecorder.resolveRoundInfo(roundIndex);

            builder.startListItem("id: " + (roundIndex + 1));
            builder.appendLine("label: " + quote(roundInfo.label()));
            builder.appendLine("description: " + quote(roundInfo.description()));
            builder.appendLine("slots:");
            builder.increaseIndent();
            Map<Integer, String> lastStates = new HashMap<>();

            for (int slot = 0; slot < slotCount; slot++) {
                builder.startListItem("id: " + (slot + 1));

                CtNetworkTime timeKey = new CtNetworkTime(roundIndex, slot);
                Map<Integer, String> updates = history.getOrDefault(timeKey, Collections.emptyMap());
                if (!updates.isEmpty()) {
                    updates.forEach(lastStates::put);
                }

                builder.appendLine("nodeStates:");
                builder.increaseIndent();
                for (CtNode node : sortedNodes) {
                    builder.appendLine(node.getId() + ": "
                            + quoteNullable(lastStates.get(node.getId())));
                }
                builder.decreaseIndent();

                builder.appendLine("knowledge:");
                builder.increaseIndent();
                for (CtNode node : sortedNodes) {
                    builder.appendLine(node.getId() + ": null");
                }
                builder.decreaseIndent();

                List<BlueFloodScenarioRecorder.TransmissionEvent> slotEvents = events.get(timeKey);
                if (slotEvents != null && !slotEvents.isEmpty()) {
                    builder.appendLine("events:");
                    builder.increaseIndent();
                    for (BlueFloodScenarioRecorder.TransmissionEvent event : slotEvents) {
                        builder.startListItem("type: " + quote(event.type()));
                        builder.appendLine("from: " + event.from());
                        builder.appendLine("to: " + event.to());
                        builder.appendLine("success: " + event.success());
                        if (event.failureReason() != null) {
                            builder.appendLine("failureReason: " + quote(event.failureReason().name()));
                        }
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

        Files.createDirectories(outputPath.getParent());
        try (var writer = Files.newBufferedWriter(outputPath)) {
            writer.write(builder.toString());
        }
    }

    private int determineSlotCount(int roundIndex,
            SortedMap<CtNetworkTime, Map<Integer, String>> history,
            SortedMap<CtNetworkTime, List<BlueFloodScenarioRecorder.TransmissionEvent>> events) {
        int maxSlot = -1;
        for (CtNetworkTime time : history.keySet()) {
            if (time.round() == roundIndex) {
                maxSlot = Math.max(maxSlot, time.slot());
            }
        }
        for (CtNetworkTime time : events.keySet()) {
            if (time.round() == roundIndex) {
                maxSlot = Math.max(maxSlot, time.slot());
            }
        }

        int computed = strategies.transmissionPolicy().getTotalSlotsOfRound();
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

    private CtNetworkTime resolveNetworkTime(ContextView context) {
        CtNetworkTime networkTime = getNetworkTime();
        if (networkTime == null && context != null) {
            networkTime = strategies.transmissionPolicy().getNetworkTime(context.getTime());
            updateNetworkTime(networkTime);
        }
        return networkTime;
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
