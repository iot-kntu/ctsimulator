package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;
import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;
import ir.ac.kntu.SynchronousTransmission.events.StInitiateFloodEvent;

import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;

public abstract class BlueFloodBaseApplication extends BaseApplication {

    protected final BlueFloodStrategies strategies;
    private final BlueFloodSettings settings;
    private final HashMap<Node, FloodStrategy> nodeFloodStrategies;
    private StNetworkTime networkTime;

    public BlueFloodBaseApplication(BlueFloodSettings settings, BlueFloodStrategies strategies) {
        this.settings = settings;
        this.strategies = strategies;
        this.nodeFloodStrategies = new HashMap<>();
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

        final TransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        this.networkTime = transmissionPolicy.getNetworkTime(context.getTime());

        super.simulationTimeProgressed(context);
    }

    @Override
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final Node inode = getInitiatorNode(context);
        strategies.transmissionPolicy().newRound(networkTime, inode);

        context.getSimulator().scheduleEvent(
                new StNewRoundEvent(context.getTime() + strategies.transmissionPolicy().getTotalSlotsOfRound()));

        final StMessage<?> message = buildMessage(context);
        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() +
                " initiated message [" + message.messageNo() + "]");

        final FloodStrategy floodStrategy = getNodeFloodStrategy(inode);
        floodStrategy.floodMessage(context, inode, message);

        strategies.transmissionPolicy().printCurrentNodeStates();

        next().initiateFlood(context);
    }

    @Override
    public Node getInitiatorNode(ContextView context) {
        return context.getNetGraph().getNodeById(strategies.initiatorStrategy().getCurrentInitiatorId());
    }

    @Override
    public void packetReceived(StFloodPacket<?> packet, ContextView context) {
        Objects.requireNonNull(packet);
        Objects.requireNonNull(context);

        final Node receiver = packet.getReceiver();
        switch (getNodeState(receiver)) {

            case Sleep -> {
            }
            case Listen -> {
                getLogger().log(Level.INFO, String.format("[t:%d-r:%d-s:%d] node[%d] received Pkt[%d]",
                                                          context.getTime(),
                                                          networkTime.round(),
                                                          networkTime.slot(),
                                                          receiver.getId(), packet.getStMessage().messageNo()));

                strategies.transmissionPolicy().newPacketReceived(receiver, getSlot());

                final FloodStrategy floodStrategy = getNodeFloodStrategy(receiver);
                floodStrategy.floodMessage(context, receiver, packet.getStMessage());
            }
            case Flood -> getLogger().log(Level.WARNING, "Received packet while in the flooding state");
        }

        strategies.transmissionPolicy().printCurrentNodeStates();

        super.packetReceived(packet, context);
    }

    @Override
    public NodeState getNodeState(Node node){
        return strategies.transmissionPolicy().getNodeState(node, getSlot());
    }

    @Override
    public void newRound(ContextView context) {
        if (this.networkTime != null && this.networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if (getRound() < settings.roundLimit()) {
            final int nextInitiator = strategies.initiatorStrategy().getNextInitiatorId();
            StInitiateFloodEvent initiateFloodEvent = new StInitiateFloodEvent(context.getTime(), nextInitiator);
            context.getSimulator().scheduleEvent(initiateFloodEvent);
        }
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

    private FloodStrategy getNodeFloodStrategy(Node inode) {
        FloodStrategy floodStrategy = nodeFloodStrategies.get(inode);
        if (floodStrategy == null) {
            try {
                floodStrategy = inode.getFloodStrategy().getStrategy(settings);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }

            nodeFloodStrategies.put(inode, floodStrategy);
        }
        return floodStrategy;
    }


}

