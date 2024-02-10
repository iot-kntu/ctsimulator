package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;
import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;
import ir.ac.kntu.SynchronousTransmission.events.StInitiateFloodEvent;

import java.util.*;
import java.util.logging.Level;

// TODO: 1/24/24 we may cleanup the messageToRcvdNodes map as previous messages are not useful

public abstract class BlueFloodBaseApplication extends BaseApplication {

    protected final static HashMap<Integer, HashSet<Node>> messageToRcvdNodes = new HashMap<>();
    protected final Random random = new Random(new Date().getTime());
    protected final BlueFloodSettings settings;
    private StNetworkTime networkTime;

    private StateLogger stateLogger;
    /**
     * Keeps current state of the nodes
     */
    private Map<Node, NodeState> nodeStates;

    public BlueFloodBaseApplication(BlueFloodSettings settings) {
        this.settings = settings;
    }

    public abstract StMessage<?> buildMessage(ContextView context);

    @Override
    public void simulationStarting(ContextView context) {

        final int nextInitiator = settings.initiatorStrategy().getNextInitiatorId();
        StInitiateFloodEvent initiateFloodEvent = new StInitiateFloodEvent(context.getTime(), nextInitiator);
        context.getSimulator().scheduleEvent(initiateFloodEvent);

        super.simulationStarting(context);
    }

    @Override
    public void simulationFinishing(ContextView context) {
        super.simulationFinishing(context);
    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        if (stateLogger == null)
            stateLogger = new StateLogger(context.getNetGraph().getNodes());

        this.networkTime = settings.transmissionPolicy().getNetworkTime(context.getTime());

        stateLogger.timeForward(networkTime);

        next().simulationTimeProgressed(context);
    }

    @Override
    public void initiateFlood(ContextView context) {
        Objects.requireNonNull(context);

        final Node inode = context.getNetGraph().getNodeById(settings.initiatorStrategy().getCurrentInitiatorId());
        settings.transmissionPolicy().newRoundStarted(inode);

        final StMessage<?> message = buildMessage(context);
        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() +
                " initiated message [" + message.messageNo() + "]");

        floodMessage(context, inode, message);

        next().initiateFlood(context);
    }

    @Override
    public void packetReceived(StFloodPacket<?> packet, ContextView context) {
        Objects.requireNonNull(packet);
        Objects.requireNonNull(context);

        final Node receiver = packet.getReceiver();

        getLogger().log(Level.INFO, String.format("[t:%d-r:%d-s:%d] node-%d received Pkt[%d]",
                                                  context.getTime(),
                                                  networkTime.round(),
                                                  networkTime.slot(),
                                                  receiver.getId(), packet.getStMessage().messageNo()));

        stateLogger.setState(networkTime, receiver, NodeState.Listen);

        if (!messageToRcvdNodes.containsKey(packet.getStMessage().messageNo()))
            messageToRcvdNodes.put(packet.getStMessage().messageNo(), new HashSet<>());

        final HashSet<Node> messageReceivers = messageToRcvdNodes.get(packet.getStMessage().messageNo());

        // check to see if a node previously has received the packet
        //  if no, flood it; otherwise stop flooding
        if (!messageReceivers.contains(receiver)) {
            messageReceivers.add(receiver);

            if (messageReceivers.size() == context.getNetGraph().getNodeCount())
                roundFinished(context);
            else
                floodMessage(context, receiver, packet.getStMessage());
        }

        super.packetReceived(packet, context);
    }

    public void roundFinished(ContextView context) {

        // FIXME: 2/4/24 fix this
        logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");
        //context.round++;
        //context.slot = 1;
        //
        //if (settings.initiatorStrategy() == StInitiatorStrategy.RoundRobin) {
        //    turnNodeIndex = (turnNodeIndex + 1) % context.netGraph.getNodeCount();
        //}
        //
        //context.roundInitiator = context.netGraph.getNodes().get(turnNodeIndex);
        //StInitiateFloodEvent initiateFloodEvent = new StInitiateFloodEvent(context.time + 1, nextInitiator);
        //scheduleEvent(initiateFloodEvent);
        //context.getApplication().simulationTimeProgressed(context);
    }

    public String printTimeline() {
        return stateLogger.printTimeline();
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
     * also considers {@link BlueFloodSettings#lossProbability()}
     *
     * @param context simulation context
     * @param packet  packet for flooding
     */
    private void floodPacket(ContextView context, StFloodPacket<?> packet) {

        for (int i = 0; i < settings.transmissionPolicy().getFloodRepeatCount(); i++) {

            StEvent event = packet.scheduleFor(i);

            if (random.nextDouble() >= settings.lossProbability()) {
                context.getSimulator().scheduleEvent(event);
            }
        }
    }

    private NodeState getNodeState(Node node, int nodesCount) {

        //fixme: implement
        return NodeState.Sleep;

        //int totalSlotsOfRound = graphDiameter + floodRepeatCount;
        //int round = (int) (1.0 * time / totalSlotsOfRound);
        //int slot = (int) (time % totalSlotsOfRound);

    }

    public int getRound(){
        return networkTime.round();
    }

    public int getSlot(){
        return networkTime.slot();
    }
}

