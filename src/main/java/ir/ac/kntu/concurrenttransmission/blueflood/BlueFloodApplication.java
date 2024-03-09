package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlueFloodApplication implements ConcurrentTransmissionApplication {

    public static double DEFAULT_INTERFERENCE_PROB = 0.9;
    protected final BlueFloodStrategies strategies;
    private final Logger logger = Logger.getLogger("BlueFloodApplication");
    private final BlueFloodSettings settings;
    private final Random random = new Random(new Date().getTime());
    private CtNetworkTime networkTime;
    private final SortedMap<CtNode, BlueFloodNodeListener> listeners;

    public BlueFloodApplication(BlueFloodSettings settings, BlueFloodStrategies strategies) {
        this.settings = settings;
        this.strategies = strategies;
        this.listeners = new TreeMap<>();
    }

    public void setListener(CtNode node, BlueFloodNodeListener listener) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(listener);

        this.listeners.put(node, listener);
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

        final TransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        this.networkTime = transmissionPolicy.getNetworkTime(context.getTime());
    }

    @Override
    public void newRound(ContextView context) {
        if (this.networkTime != null && this.networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if (getRound() < settings.roundLimit()) {
            final int nextInitiator = strategies.initiatorStrategy().getNextInitiatorId();
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

        strategies.transmissionPolicy().newRound(networkTime, inode);

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
        //  to be received.
        FloodPacket<?> thePacket = packets.size() == 1
                ? packets.get(0)
                : packets.get(random.nextInt(packets.size()));

        final CtNode receiver = ctEvent.getReceiver();

        switch (getNodeState(receiver)) {

            case Sleep -> {
            }
            case Listen -> {

                strategies.transmissionPolicy().newPacketReceived(receiver, getSlot());

                double receiveProbability = ctEvent.areMessagesSimilar()
                        ? settings.lossProbability()
                        : settings.conflictProbability();

                if (random.nextDouble() >= receiveProbability) { // no loss

                    getLogger().log(Level.INFO, String.format("[t:%d-r:%d-s:%d] node[%d] received Pkt[%d]",
                                                              context.getTime(),
                                                              networkTime.round(),
                                                              networkTime.slot(),
                                                              receiver.getId(), thePacket.ciMessage().messageNo()));

                    getBlueFloodListener(receiver).ctPacketsReceived(context, packets, thePacket, ctEvent.areMessagesSimilar());
                    receiver.floodMessage(context, receiver, thePacket.ciMessage());
                }
                else {
                    getBlueFloodListener(receiver).ctPacketsLost(context, packets, ctEvent.areMessagesSimilar());
                }
            }
            case Flood -> {
                //getLogger().log(Level.WARNING, "Received packet while in the flooding state");
            }
        }

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
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        return getBlueFloodListener(sender).getMessage(context, sender, receivedMessage, whichRepeat);
    }

    @Override
    public CiMessage<?> getRoundInitiationMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return getBlueFloodListener(initiator).initiateMessage(context, initiator, whichRepeat);
    }

    public Logger getLogger() {
        return logger;
    }

    public String printTimeline() {
        return strategies.transmissionPolicy().printHistory();
    }

    public int getRound() {
        return networkTime.round();
    }

    public int getSlot() {
        return networkTime.slot();
    }

    private BlueFloodNodeListener getBlueFloodListener(CtNode node) {
        final BlueFloodNodeListener listener = listeners.get(node);
        if (listener == null)
            throw new IllegalStateException("No listener defined for node " + node);

        return listener;
    }
}

