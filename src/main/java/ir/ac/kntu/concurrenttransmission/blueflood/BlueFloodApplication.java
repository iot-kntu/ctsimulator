package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.AbstractConcurrentTransmissionApplication;
import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlueFloodApplication extends AbstractConcurrentTransmissionApplication<BlueFloodNodeListener>
        implements CtBlueFloodApplication {

    public static double DEFAULT_INTERFERENCE_PROB = 0.9;
    protected final BlueFloodStrategies strategies;
    private final Logger logger = Logger.getLogger("BlueFloodApplication");
    private final BlueFloodSettings settings;
    private final Random random = new Random(new Date().getTime());

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
        newRound(context);
    }

    @Override
    public void simulationFinishing(ContextView context) {

    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        final TransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        updateNetworkTime(transmissionPolicy.getNetworkTime(context.getTime()));
    }

    @Override
    public void newRound(ContextView context) {
        if (getNetworkTime() != null && getNetworkTime().round() > 0)
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

        strategies.transmissionPolicy().newRound(getNetworkTime(), inode);

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
                            getRound(),
                            getSlot(),
                            receiver.getId(), thePacket.ctMessage().messageNo()));

                    boolean shouldFlood = getBlueFloodListener(receiver).ctPacketsReceived(context, packets, thePacket,
                            ctEvent.areMessagesSimilar());
                    if (shouldFlood)
                        receiver.floodMessage(context, receiver, thePacket.ctMessage());
                } else {
                    getBlueFloodListener(receiver).ctPacketsLost(context, packets, ctEvent.areMessagesSimilar());
                }
            }
            case Flood -> {
                // getLogger().log(Level.WARNING, "Received packet while in the flooding
                // state");
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

    private BlueFloodNodeListener getBlueFloodListener(CtNode node) {
        return getListener(node);
    }
}
