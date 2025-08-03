package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;
import ir.ac.kntu.concurrenttransmission.nodes.CtNode;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChaosApplication implements ConcurrentTransmissionApplication {

    private final Logger logger = Logger.getLogger("ChaosApplication");

    /**
     * The minimum signal difference (in dB) required for the strongest signal
     * to be successfully captured over the interference from other signals.
     * A common value from literature is 3 dB.
     */
    private static final double CAPTURE_THRESHOLD_DB = 0;

    protected final ChaosStrategies strategies;

    private final ChaosSettings settings;

    private final Random random = new Random(new Date().getTime());

    private CtNetworkTime networkTime;

    private final SortedMap<CtNode, ChaosNodeListener> listeners;

    private final Map<CtNode, CtMessage<ChaosMessage>> nodeKnowledge;
    private final StateLogger stateLogger;
    private final Map<CtNode, Integer> finalFloodCounter;
    private final SignalModel signalModel;

    public ChaosApplication(ChaosSettings settings, ChaosStrategies strategies, NetGraph netGraph) {
        this.settings = settings;
        this.strategies = strategies;
        this.listeners = new TreeMap<>();
        this.stateLogger = new StateLogger(new ArrayList<>(netGraph.getNodes()), strategies.transmissionPolicy());
        this.nodeKnowledge = new TreeMap<>();
        this.finalFloodCounter = new TreeMap<>();
        this.signalModel = new SignalModel();

    }

    public void setListener(CtNode node, ChaosNodeListener listener) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(listener);

        this.listeners.put(node, listener);
    }

    public StateLogger getStateLogger() {
        return this.stateLogger;
    }

    @Override
    public CtNetworkTime getNetworkTime() {
        return networkTime;
    }

    @Override
    public void simulationStarting(ContextView context) {
        newRound(context);
    }


    @Override
    public void simulationFinishing(ContextView context) {

    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        final ChaosTransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        this.networkTime = transmissionPolicy.getNetworkTime(context.getTime());
    }

    @Override
    public void newRound(ContextView context) {
        if (this.networkTime != null && this.networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if (getRound() < settings.roundLimit()) {
            nodeKnowledge.clear();
            finalFloodCounter.clear();

            context.getNetGraph().getNodes().forEach(node -> {
                CtMessage<ChaosMessage> initialMessage = (CtMessage<ChaosMessage>) getChaosListener(node).newRound(context, node);
                nodeKnowledge.put(node, initialMessage);
                for (int s = 0; s < strategies.transmissionPolicy().getTotalSlotsOfRound(); s++) {
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), s), node, NodeState.Listen);
                }
            });

            final int nextInitiator = strategies.initiatorStrategy().getNextInitiatorId();
            SimInitiateFloodEvent initiateFloodEvent = new SimInitiateFloodEvent(context.getTime(), nextInitiator);
            context.getSimulator().scheduleEvent(initiateFloodEvent);

            // scheduling the next round to keep the simulation working even with faults
            context.getSimulator().scheduleEvent(new SimNewRoundEvent(context.getTime() + strategies.transmissionPolicy().getTotalSlotsOfRound()));
        }
    }

    @Override
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final CtNode inode = getInitiatorNode(context);


        stateLogger.setState(getNetworkTime(), inode, NodeState.Flood);
        stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + 1), inode, NodeState.Listen);

        inode.initiateFlood(context, inode);


        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() + " initiated message");

    }

    @Override
    public void ctPacketsReceived(CtPacketsEvent ctEvent, ContextView context) {
        Objects.requireNonNull(ctEvent);
        Objects.requireNonNull(context);

        final List<FloodPacket<?>> packets = ctEvent.getPackets();
        System.out.println("-----------");
        System.out.println(getNetworkTime());
        System.out.println(packets);
        System.out.println("-----------");

        if (packets.isEmpty()) return;

        final CtNode receiver = ctEvent.getReceiver();

        // --- FINAL FLOOD LOGIC: Check if node is already done ---
        if (finalFloodCounter.containsKey(receiver) && finalFloodCounter.get(receiver) <= 0) {
            return;
        }

        stateLogger.setState(getNetworkTime(), receiver, NodeState.Listen);

        // If only one packet was received, there's no interference. Process it directly.
        if (packets.size() == 1) {
            processCapturedPacket(packets.get(0), context);
            return;
        }

        // --- NEW: Physics-based Capture Effect Logic ---
        FloodPacket<?> strongestPacket = null;
        double maxSignalStrengthDb = -Double.MAX_VALUE;
        double totalInterferencePowerMw = 0;

        // Find the strongest packet and sum the power of all other packets (interference).
        for (FloodPacket<?> packet : packets) {
            double distance = context.getNetGraph().getDistanceBetween(packet.sender(), receiver);
            double signalStrengthDb = signalModel.calculateSignalStrengthDb(distance);

            if (signalStrengthDb > maxSignalStrengthDb) {
                // The previously strongest signal is now part of the interference.
                if (strongestPacket != null) {
                    totalInterferencePowerMw += signalModel.dbmToMilliwatts(maxSignalStrengthDb);
                }
                // We have a new strongest signal.
                maxSignalStrengthDb = signalStrengthDb;
                strongestPacket = packet;
            } else {
                // This packet is interference.
                totalInterferencePowerMw += signalModel.dbmToMilliwatts(signalStrengthDb);
            }
        }

        // Apply the capture effect rule.
        double maxSignalPowerMw = signalModel.dbmToMilliwatts(maxSignalStrengthDb);

        // Check for division by zero if there's no interference.
        if (totalInterferencePowerMw <= 0) {
            processCapturedPacket(strongestPacket, context);
            return;
        }

        double signalToInterferenceRatioDb = 10 * Math.log10(maxSignalPowerMw / totalInterferencePowerMw);

        if (signalToInterferenceRatioDb >= CAPTURE_THRESHOLD_DB) {
            // Capture was successful.
            logger.log(Level.INFO, String.format("[t:%d] Capture SUCCESS at Node[%d]. Packet from Node[%d] (%.2fdB) won over interference (%.2fdB)",
                    context.getTime(), receiver.getId(), strongestPacket.sender().getId(), maxSignalStrengthDb, signalModel.milliwattsToDbm(totalInterferencePowerMw)));
            processCapturedPacket(strongestPacket, context);
        } else {
            // Capture failed; all packets are lost.
            logger.log(Level.WARNING, String.format("[t:%d] Capture FAILED at Node[%d]. Strongest packet (%.2fdB) was not strong enough over interference (%.2fdB)",
                    context.getTime(), receiver.getId(), maxSignalStrengthDb, signalModel.milliwattsToDbm(totalInterferencePowerMw)));
            getChaosListener(receiver).ctPacketsLost(context, packets, false);
        }
    }


    /**
     * Helper method to process a single packet that has been successfully captured.
     * This contains the core Chaos logic for merging and deciding whether to flood.
     *
     * @param capturedPacket The packet that won the capture effect contention.
     * @param context        The current simulation context.
     */
    private void processCapturedPacket(FloodPacket<?> capturedPacket, ContextView context) {
        CtNode receiver = capturedPacket.receiver();

        CtMessage<ChaosMessage> receivedMessage = (CtMessage<ChaosMessage>) capturedPacket.ctMessage();
        CtMessage<ChaosMessage> currentMessage = nodeKnowledge.get(receiver);
        ChaosMessage currentContent = currentMessage.content();
        ChaosMessage receivedContent = receivedMessage.content();

        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) getChaosListener(receiver).merge(context, capturedPacket);
        nodeKnowledge.put(receiver, mergedMessage);
        ChaosMessage mergedContent = mergedMessage.content();


        int totalNodes = context.getNetGraph().getNodeCount();
        if (mergedContent.flags().cardinality() == totalNodes && !finalFloodCounter.containsKey(receiver)) {
            finalFloodCounter.put(receiver, getTransmissionPolicy().getFinalFloodRepeatCount());
            logger.log(Level.INFO, "Node " + receiver.getId() + " has reached COMPLETION. Starting final flood.");
        }


        boolean isInFinalFlood = finalFloodCounter.getOrDefault(receiver, 0) > 0;
        boolean hasNewInfo = !mergedContent.flags().equals(currentContent.flags());
        boolean receiverKnowsMore = currentContent.flags().cardinality() > receivedContent.flags().cardinality();


        if (isInFinalFlood || hasNewInfo || receiverKnowsMore) {
            logger.log(Level.INFO, "Node " + receiver.getId() + " decided to flood. FinalFlood: " + isInFinalFlood + ", NewInfo: " + hasNewInfo + ", KnowsMore: " + receiverKnowsMore);

            final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(receiver);

            if (!isInFinalFlood) {
                for (CtNode node : neighbors) {
                    for (int repeat = 1; repeat <= strategies.transmissionPolicy().getFloodRepeatCount(); repeat++) {
                        final FloodPacket<?> stFloodPacket = new FloodPacket<>(context.getTime() + repeat, mergedMessage, receiver, node);
                        context.getSimulator().schedulePacket(stFloodPacket);
                    }

                    for (int repeat = 0; repeat < strategies.transmissionPolicy().getFloodRepeatCount(); repeat++) {
                        stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + repeat), receiver, NodeState.Flood);
                    }
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + strategies.transmissionPolicy().getFloodRepeatCount()), receiver, NodeState.Listen);

                }
            }


            if (isInFinalFlood) {
                int remainingFloods = finalFloodCounter.get(receiver);

                for (CtNode node : neighbors) {
                    for (int repeat = 1; repeat <= remainingFloods; repeat++) {
                        final FloodPacket<?> stFloodPacket = new FloodPacket<>(context.getTime() + repeat, mergedMessage, receiver, node);
                        context.getSimulator().schedulePacket(stFloodPacket);
                    }
                }

                for (int repeat = 1; repeat <= remainingFloods; repeat++) {
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + repeat), receiver, NodeState.Flood);
                }

                finalFloodCounter.put(receiver, 0);
                logger.log(Level.INFO, "Node " + receiver.getId() + " finished final flood. Will sleep.");

                for (int s = remainingFloods; s < strategies.transmissionPolicy().getTotalSlotsOfRound(); s++) {
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + s + 1), receiver, NodeState.Sleep);
                }
            }
        }

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
    public ChaosTransmissionPolicy getTransmissionPolicy() {
        return strategies.transmissionPolicy();
    }


    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return getChaosListener(sender).getMessage(context, sender, receivedMessage, whichRepeat);
    }

    @Override
    public CtMessage<?> getRoundInitiationMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return getChaosListener(initiator).getRoundMessage(context, initiator, whichRepeat);
    }


    public Logger getLogger() {
        return logger;
    }

    public int getRound() {
        return networkTime.round();
    }

    public int getSlot() {
        return networkTime.slot();
    }

    private ChaosNodeListener getChaosListener(CtNode node) {
        final ChaosNodeListener listener = listeners.get(node);
        if (listener == null) throw new IllegalStateException("No listener defined for node " + node);

        return listener;
    }
}
