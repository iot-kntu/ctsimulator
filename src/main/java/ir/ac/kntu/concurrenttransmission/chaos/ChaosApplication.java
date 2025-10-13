package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.FinalFloodingState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.SleepingState;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chaos application that implements dynamic round completion detection.
 * Rounds complete when all nodes reach SleepingState or when timeout (256
 * slots) is reached.
 */
public class ChaosApplication implements CtChaosApplication {

    private static final Logger logger = Logger.getLogger(ChaosApplication.class.getSimpleName());

    /**
     * The minimum signal difference (in dB) required for the strongest signal
     * to be successfully captured over the interference from other signals.
     * A common value from literature is 3 dB.
     */
    private static final double CAPTURE_THRESHOLD_DB = 0;
    protected final ChaosStrategies strategies;
    private final ChaosSettings settings;
    private CtNetworkTime networkTime;
    private final SortedMap<CtNode, ChaosNodeListener> listeners;
    private final ChaosStateLogger stateLogger;
    private final SignalModel signalModel;
    private final NodeState startingPoint;
    private long roundStartTime;
    private boolean roundCompleted = false;
    private int nodesInFinalFlooding = 0;
    private int nodesInSleeping = 0;

    public ChaosApplication(ChaosSettings settings, ChaosStrategies strategies, NetGraph netGraph,
                            NodeState startingPoint) {
        this.settings = settings;
        this.strategies = strategies;
        this.listeners = new TreeMap<>();
        this.stateLogger = new ChaosStateLogger(new ArrayList<>(netGraph.getNodes()), strategies.transmissionPolicy());
        this.signalModel = new SignalModel();
        this.startingPoint = startingPoint;
    }

    public void setListener(CtNode node, ChaosNodeListener listener) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(listener);

        this.listeners.put(node, listener);
    }

    /**
     * Called when a node's state changes to track completion conditions
     */
    public void onNodeStateChanged(StatefulNode node, NodeState oldState, NodeState newState) {
        if (oldState instanceof FinalFloodingState) {
            nodesInFinalFlooding--;
        } else if (oldState instanceof SleepingState) {
            nodesInSleeping--;
        }

        if (newState instanceof FinalFloodingState) {
            nodesInFinalFlooding++;
        } else if (newState instanceof SleepingState) {
            nodesInSleeping++;
        }
    }


    public ChaosStateLogger getStateLogger() {
        return this.stateLogger;
    }

    @Override
    public CtNetworkTime getNetworkTime() {
        return networkTime;
    }

    @Override
    public void simulationStarting(ContextView context) {
        roundStartTime = context.getTime();
        roundCompleted = false;
        newRound(context);
    }

    @Override
    public void simulationFinishing(ContextView context) {
        strategies.transmissionPolicy().endRound(context.getTime() + 1);
    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        final ChaosTransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        this.networkTime = transmissionPolicy.getNetworkTime(context.getTime());

        for (CtNode node : context.getNetGraph().getNodes()) {
            if (node instanceof StatefulNode statefulNode) {
                if (statefulNode.getCurrentState() != null) {
                    statefulNode.getCurrentState().onSlotStart(statefulNode, context);
                }
            }
        }

        // Check for timeout
        if (!roundCompleted && (context.getTime() - roundStartTime) >= transmissionPolicy.getTotalSlotsOfRound()) {
            logger.log(Level.INFO, "Round timeout reached at slot " + (context.getTime() - roundStartTime));
            completeRound(context);
        }
    }

    @Override
    public void newRound(ContextView context) {
        if (this.networkTime != null && this.networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if (getRound() < settings.roundLimit()) {
            roundStartTime = context.getTime();
            roundCompleted = false;

            // Reset state counters
            nodesInFinalFlooding = 0;
            nodesInSleeping = 0;

            logger.log(Level.INFO, "Starting new round " + (getRound() + 1) + " at time " + context.getTime());

            final int nextInitiatorId = strategies.initiatorStrategy().getNextInitiatorId();
            context.getNetGraph().getNodes().forEach(node -> {
                if (node instanceof StatefulNode) {
                    CtMessage<ChaosMessage> initialMessage = getChaosNodeListener(node)
                            .initiateMessage(context, node, context.getNetGraph().getNodeById(nextInitiatorId));
                    ((StatefulNode) node).initializeForNewRound(context, initialMessage, this.stateLogger,
                            startingPoint);

                    // Update state counters based on initial state
                    if (startingPoint instanceof FinalFloodingState) {
                        nodesInFinalFlooding++;
                    } else if (startingPoint instanceof SleepingState) {
                        nodesInSleeping++;
                    }
                }
            });

            SimInitiateFloodEvent initiateFloodEvent = new SimInitiateFloodEvent(context.getTime(), nextInitiatorId);
            context.getSimulator().scheduleEvent(initiateFloodEvent);
        } else {
            logger.log(Level.INFO, "All rounds completed. Simulation finished.");
        }
    }

    public void checkRoundCompletion(ContextView context) {
        if (roundCompleted)
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

        int totalNodes = listeners.size();
        boolean allSleeping = nodesInSleeping == totalNodes;

        if (allSleeping) {
            completeRound(context);
        }
    }

    private void completeRound(ContextView context) {
        if (roundCompleted)
            return;

        roundCompleted = true;
        long actualSlotsUsed = context.getTime() - roundStartTime;
        logger.log(Level.INFO, "Round completed in " + actualSlotsUsed + " slots");
        strategies.transmissionPolicy().endRound(context.getTime() + 1);

        // Schedule next round
        context.getSimulator().scheduleEvent(new SimNewRoundEvent(context.getTime() + 1));
    }

    @Override
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final CtNode initiator = getInitiatorNode(context);
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
        FloodPacket<?> capturedPacket = selectPacketBySignal(packets, (StatefulNode) receiver, context);

        if (capturedPacket != null) {
            ((StatefulNode) receiver).handlePacket(context, capturedPacket);
        } else {
            getChaosNodeListener(receiver).ctPacketsLost(context, packets, false);
        }
    }

    private FloodPacket<?> selectPacketBySignal(List<FloodPacket<?>> packets, StatefulNode receiver,
                                                ContextView context) {
        if (packets.size() == 1) {
            return packets.get(0);
        }

        FloodPacket<?> strongestPacket = null;
        double maxSignalStrengthDb = -Double.MAX_VALUE;
        double totalInterferencePowerMw = 0;

        for (FloodPacket<?> packet : packets) {
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
        final ChaosNodeListener listener = listeners.get(node);
        if (listener == null) {
            throw new IllegalStateException("No listener defined for node " + node);
        }
        return listener;
    }

    public Logger getLogger() {
        return logger;
    }

    public int getRound() {
        return this.networkTime != null ? this.networkTime.round() : 0;
    }

    public int getSlot() {
        return this.networkTime != null ? this.networkTime.slot() : 0;
    }

}
