package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;
import ir.ac.kntu.SynchronousTransmission.events.CtPacketsEvent;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;
import ir.ac.kntu.SynchronousTransmission.events.SimInitiateFloodEvent;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BlueFloodBaseApplication implements ConcurrentTransmissionApplication {

    private final Logger logger = Logger.getLogger("BlueFloodApplication");

    protected final BlueFloodStrategies strategies;
    private final BlueFloodSettings settings;
    private final HashMap<Node, NodeFloodStrategy> nodeFloodStrategies;
    private final Random random = new Random(new Date().getTime());
    private CtNetworkTime networkTime;

    public BlueFloodBaseApplication(BlueFloodSettings settings, BlueFloodStrategies strategies) {
        this.settings = settings;
        this.strategies = strategies;
        this.nodeFloodStrategies = new HashMap<>();
    }

    public abstract CiMessage<?> buildMessage(ContextView context);

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
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final Node inode = getInitiatorNode(context);

        strategies.transmissionPolicy().newRound(networkTime, inode);

        // scheduling the next round to keep the simulation working even with faults
        context.getSimulator().scheduleEvent(
                new SimNewRoundEvent(context.getTime() + strategies.transmissionPolicy().getTotalSlotsOfRound()));

        final CiMessage<?> message = buildMessage(context);
        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() +
                " initiated message [" + message.messageNo() + "]");

        final NodeFloodStrategy floodStrategy = getNodeFloodStrategy(inode);
        floodStrategy.floodMessage(context, inode, message);

        strategies.transmissionPolicy().printCurrentNodeStates();
    }

    @Override
    public void ctPacketsReceived(CtPacketsEvent ctEvent, ContextView context) {
        Objects.requireNonNull(ctEvent);
        Objects.requireNonNull(context);

        final List<FloodPacket<?>> packets = ctEvent.getPackets();

        if (packets.isEmpty())
            return;

        FloodPacket<?> thePacket = packets.size() == 1
                ? packets.get(0)
                : packets.get(random.nextInt(packets.size()));

        final Node receiver = ctEvent.getReceiver();

        switch (getNodeState(receiver)) {

            case Sleep -> {
            }
            case Listen -> {
                getLogger().log(Level.INFO, String.format("[t:%d-r:%d-s:%d] node[%d] received Pkt[%d]",
                                                          context.getTime(),
                                                          networkTime.round(),
                                                          networkTime.slot(),
                                                          receiver.getId(), thePacket.ciMessage().messageNo()));

                strategies.transmissionPolicy().newPacketReceived(receiver, getSlot());

                double receiveProbability = ctEvent.arePacketsSimilar()
                        ? settings.lossProbability()
                        : settings.conflictProbability();

                if (random.nextDouble() >= receiveProbability) { // no loss
                    final NodeFloodStrategy floodStrategy = getNodeFloodStrategy(receiver);
                    floodStrategy.floodMessage(context, receiver, thePacket.ciMessage());
                }
                else {
                    logger.log(Level.INFO, "PKT[" + thePacket + "] lost due to "
                            + ((ctEvent.arePacketsSimilar()) ? "general loss" : "conflict"));
                }
            }
            case Flood -> {
                //getLogger().log(Level.WARNING, "Received packet while in the flooding state");
            }
        }

        strategies.transmissionPolicy().printCurrentNodeStates();

    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public Node getInitiatorNode(ContextView context) {
        return context.getNetGraph().getNodeById(strategies.initiatorStrategy().getCurrentInitiatorId());
    }

    @Override
    public NodeState getNodeState(Node node) {
        return strategies.transmissionPolicy().getNodeState(node, getSlot());
    }

    @Override
    public void newRound(ContextView context) {
        if (this.networkTime != null && this.networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if (getRound() < settings.roundLimit()) {
            final int nextInitiator = strategies.initiatorStrategy().getNextInitiatorId();
            SimInitiateFloodEvent initiateFloodEvent = new SimInitiateFloodEvent(context.getTime(), nextInitiator);
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

    @Override
    public TransmissionPolicy getTransmissionPolicy() {
        return strategies.transmissionPolicy();
    }

    private NodeFloodStrategy getNodeFloodStrategy(Node inode) {
        NodeFloodStrategy floodStrategy = nodeFloodStrategies.get(inode);
        if (floodStrategy == null) {
            try {
                floodStrategy = inode.getFloodStrategy().getStrategy();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }

            nodeFloodStrategies.put(inode, floodStrategy);
        }
        return floodStrategy;
    }
}

