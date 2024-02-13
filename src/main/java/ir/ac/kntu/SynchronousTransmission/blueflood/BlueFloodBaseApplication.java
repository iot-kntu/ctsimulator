package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;
import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;
import ir.ac.kntu.SynchronousTransmission.events.StInitiateFloodEvent;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;

// TODO: 1/24/24 we may cleanup the messageToRcvdNodes map as previous messages are not useful

public abstract class BlueFloodBaseApplication extends BaseApplication {

    protected final Random random = new Random(new Date().getTime());
    protected final BlueFloodConfig config;

    private StNetworkTime networkTime;

    public BlueFloodBaseApplication(BlueFloodConfig config) {
        this.config = config;
    }

    public abstract StMessage<?> buildMessage(ContextView context);

    @Override
    public void simulationStarting(ContextView context) {
        newRound(context);

        super.simulationStarting(context);
    }

    @Override
    public void simulationFinishing(ContextView context) {
        super.simulationFinishing(context);
    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        final BlueFloodTransmissionPolicy transmissionPolicy = config.transmissionPolicy();
        this.networkTime = transmissionPolicy.getNetworkTime(context.getTime());

        super.simulationTimeProgressed(context);
    }

    @Override
    public void initiateFlood(ContextView context) {
        Objects.requireNonNull(context);

        final Node inode = context.getNetGraph().getNodeById(config.initiatorStrategy().getCurrentInitiatorId());
        config.transmissionPolicy().newRound(networkTime, inode);

        context.getSimulator().scheduleEvent(new StNewRoundEvent(context.getTime() + config.transmissionPolicy().getTotalSlotsOfRound()));

        final StMessage<?> message = buildMessage(context);
        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() +
                " initiated message [" + message.messageNo() + "]");

        floodMessage(context, inode, message);

        config.transmissionPolicy().printCurrentNodeStates();

        next().initiateFlood(context);
    }

    @Override
    public void packetReceived(StFloodPacket<?> packet, ContextView context) {
        Objects.requireNonNull(packet);
        Objects.requireNonNull(context);

        final Node receiver = packet.getReceiver();
        final NodeState nodeState = config.transmissionPolicy().getNodeState(receiver, getSlot());
        switch (nodeState) {

            case Sleep -> {
            }
            case Listen -> {
                getLogger().log(Level.INFO, String.format("[t:%d-r:%d-s:%d] node[%d] received Pkt[%d]",
                                                          context.getTime(),
                                                          networkTime.round(),
                                                          networkTime.slot(),
                                                          receiver.getId(), packet.getStMessage().messageNo()));

                config.transmissionPolicy().newPacketReceived(receiver, getSlot());
                floodMessage(context, receiver, packet.getStMessage());

            }
            case Flood -> getLogger().log(Level.WARNING, "Received packet while in the flooding state");
        }

        config.transmissionPolicy().printCurrentNodeStates();

        super.packetReceived(packet, context);
    }

    @Override
    public void newRound(ContextView context) {
        if (this.networkTime != null && this.networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if(getRound() < config.maxRounds()) {
            final int nextInitiator = config.initiatorStrategy().getNextInitiatorId();
            StInitiateFloodEvent initiateFloodEvent = new StInitiateFloodEvent(context.getTime(), nextInitiator);
            context.getSimulator().scheduleEvent(initiateFloodEvent);
        }
    }

    public String printTimeline() {
        return config.transmissionPolicy().printHistory();
    }

    public int getRound() {
        return networkTime.round();
    }

    public int getSlot() {
        return networkTime.slot();
    }

    protected <T> void floodMessage(ContextView context, Node sender, StMessage<T> message) {

        final List<Node> neighbors = context.getNetGraph().getNodeNeighbors(sender);

        for (Node node : neighbors) {
            final StFloodPacket<T> stEvent = new StFloodPacket<>(context.getTime() + 1, message, sender, node);
            floodPacket(context, stEvent);
        }
    }

    /**
     * Floods the given packet for {@link BlueFloodTransmissionPolicy#getFloodRepeatCount()} times, and
     * also considers {@link BlueFloodConfig#lossProbability()}
     *
     * @param context simulation context
     * @param packet  packet for flooding
     */
    private void floodPacket(ContextView context, StFloodPacket<?> packet) {

        for (int i = 0; i < config.transmissionPolicy().getFloodRepeatCount(); i++) {

            StEvent event = packet.scheduleWithDelay(i);

            if (random.nextDouble() >= config.lossProbability()) {
                context.getSimulator().scheduleEvent(event);
            }
            else {
                logger.log(Level.INFO, "PKT[" + packet + "] lost");
            }
        }
    }

}

