package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;
import ir.ac.kntu.concurrenttransmission.CtNode;

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
    private CtNetworkTime networkTime;
    private final SortedMap<CtNode, ChaosNodeListener> listeners;
    private final ChaosStateLogger stateLogger;
    private final SignalModel signalModel;

    public ChaosApplication(ChaosSettings settings, ChaosStrategies strategies, NetGraph netGraph) {
        this.settings = settings;
        this.strategies = strategies;
        this.listeners = new TreeMap<>();
        this.stateLogger = new ChaosStateLogger(new ArrayList<>(netGraph.getNodes()), strategies.transmissionPolicy());
        this.signalModel = new SignalModel();
    }

    public void setListener(CtNode node, ChaosNodeListener listener) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(listener);

        this.listeners.put(node, listener);
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
            final int nextInitiatorId = strategies.initiatorStrategy().getNextInitiatorId() + 1; // TODO: fix it
            context.getNetGraph().getNodes().forEach(node -> {
                if (node instanceof StatefulNode) {
                    CtMessage<ChaosMessage> initialMessage = (CtMessage<ChaosMessage>) getChaosNodeListener(node).initiateMessage(context, node, context.getNetGraph().getNodeById(nextInitiatorId));
                    ((StatefulNode) node).initializeForNewRound(context, initialMessage, this.stateLogger);
                }
            });

            SimInitiateFloodEvent initiateFloodEvent = new SimInitiateFloodEvent(context.getTime(), nextInitiatorId);
            context.getSimulator().scheduleEvent(initiateFloodEvent);
            context.getSimulator().scheduleEvent(new SimNewRoundEvent(context.getTime() + strategies.transmissionPolicy().getTotalSlotsOfRound()));
        }
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

        if (packets.isEmpty()) return;

        final CtNode receiver = ctEvent.getReceiver();
        if (!(receiver instanceof StatefulNode)) return; // Only process for stateful nodes
        FloodPacket<?> capturedPacket = selectPacketBySignal(packets, (StatefulNode) receiver, context);

        if (capturedPacket != null) {
            ((StatefulNode) receiver).handlePacket(context, capturedPacket);
        } else {
            getChaosNodeListener(receiver).ctPacketsLost(context, packets, false);
        }
    }

    private FloodPacket<?> selectPacketBySignal(List<FloodPacket<?>> packets, StatefulNode receiver, ContextView context) {
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

        double signalToInterferenceRatioDb = 10 * Math.log10(signalModel.dbmToMilliwatts(maxSignalStrengthDb) / totalInterferencePowerMw);

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
    public NodeState getNodeState(CtNode node) {
        return strategies.transmissionPolicy().getNodeState(node, getSlot());
    }


    @Override
    public ChaosTransmissionPolicy getTransmissionPolicy() {
        return strategies.transmissionPolicy();
    }


    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return getChaosNodeListener(sender).getMessage(context, sender, receivedMessage, whichRepeat);
    }

    @Override
    public CtMessage<?> getRoundInitiationMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return getChaosNodeListener(initiator).getRoundMessage(context, initiator, whichRepeat);
    }

    /**
     * Retrieves the specific listener for a given node.
     * The listener contains the application-specific logic (e.g., how to merge messages).
     *
     * @param node The node for which to get the listener.
     * @return The ChaosNodeListener associated with the node.
     * @throws IllegalStateException if no listener is defined for the node.
     */
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
        return networkTime.round();
    }

    public int getSlot() {
        return networkTime.slot();
    }

}
