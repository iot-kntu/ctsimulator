package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;
import ir.ac.kntu.SynchronousTransmission.events.CtPacketsEvent;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;
import ir.ac.kntu.SynchronousTransmission.events.SimInitiateFloodEvent;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlueFloodApplication implements ConcurrentTransmissionApplication {

    public static double DEFAULT_INTERFERENCE_PROB = 0.9;

    private final Logger logger = Logger.getLogger("BlueFloodApplication");

    protected final BlueFloodStrategies strategies;
    private final BlueFloodSettings settings;
    private final Random random = new Random(new Date().getTime());
    private CtNetworkTime networkTime;

    public BlueFloodApplication(BlueFloodSettings settings, BlueFloodStrategies strategies) {
        this.settings = settings;
        this.strategies = strategies;
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
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final CtNode inode = getInitiatorNode(context);

        strategies.transmissionPolicy().newRound(networkTime, inode);

        // scheduling the next round to keep the simulation working even with faults
        context.getSimulator().scheduleEvent(
                new SimNewRoundEvent(context.getTime() + strategies.transmissionPolicy().getTotalSlotsOfRound()));

        final CiMessage<?> message = inode.initiateFlood(context);
        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() +
                " initiated message [" + message.messageNo() + "]");

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

        final CtNode receiver = ctEvent.getReceiver();

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
                    receiver.floodMessage(context, receiver, thePacket.ciMessage());
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
    public CtNode getInitiatorNode(ContextView context) {
        return context.getNetGraph().getNodeById(strategies.initiatorStrategy().getCurrentInitiatorId());
    }

    @Override
    public NodeState getNodeState(CtNode node) {
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
}

