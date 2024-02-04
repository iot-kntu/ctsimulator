package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.applications.BaseApplication;
import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.*;
import java.util.logging.Level;

public abstract class BlueFloodBaseApplication extends BaseApplication {

    private final static HashMap<Integer, HashSet<Node>> messageToRcvdNodes = new HashMap<>();
    private final Random random = new Random(new Date().getTime());
    private Tracer tracer;

    public abstract StMessage<?> buildMessage(ReadOnlyContext context);

    @Override
    public void onTimeProgress(ReadOnlyContext context) {
        Objects.requireNonNull(context);

        if (tracer == null)
            tracer = new Tracer(context.getNetGraph().getNodes());

        tracer.timeForward(context.getRound(), context.getSlot());

        next().onTimeProgress(context);
    }

    @Override
    public void onInitiateFlood(ReadOnlyContext context) {
        Objects.requireNonNull(context);

        final Node node = context.getRoundInitiator();

        tracer.setState(context.getRound(), context.getSlot(), node, NodeState.Flood);

        final StMessage<?> message = buildMessage(context);
        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" +
                context.getRoundInitiator().getId() + " initiated message [" + message.messageNo() + "]");

        floodMessage(context, node, message);

        next().onInitiateFlood(context);
    }

    @Override
    public void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {
        Objects.requireNonNull(packet);
        Objects.requireNonNull(context);

        final Node receiver = packet.getReceiver();

        tracer.setState(context.getRound(), context.getSlot(), receiver, NodeState.Listen);

        if (!messageToRcvdNodes.containsKey(packet.getStMessage().messageNo()))
            messageToRcvdNodes.put(packet.getStMessage().messageNo(), new HashSet<>());

        final HashSet<Node> messageReceivers = messageToRcvdNodes.get(packet.getStMessage().messageNo());

        // check to see if a node previously has received the packet
        //  if no, flood it; otherwise stop flooding
        if (!messageReceivers.contains(receiver)) {
            messageReceivers.add(receiver);

            if (messageReceivers.size() == context.getNetGraph().getNodeCount())
                context.getSimulator().roundFinished();
            else
                floodMessage(context, receiver, packet.getStMessage());
        }

        super.onPacketReceive(packet, context);
    }

    public String printTimeline() {
        return tracer.printTimeline();
    }

    protected <T> void floodMessage(ReadOnlyContext context, Node sender, StMessage<T> message) {

        final List<Node> neighbors = context.getNetGraph().getNodeNeighbors(sender);

        for (Node node : neighbors) {
            final StFloodPacket<T> stEvent = new StFloodPacket<>(context.getTime() + 1,
                                                                 message, sender, node);
            floodPacket(context, stEvent);
        }
    }

    /**
     * Floods the given packet for {@link SimulationSettings#floodRepeats()} times, and
     * also considers {@link SimulationSettings#lossProbability()}
     *
     * @param context simulation context
     * @param packet  packet for flooding
     */
    private void floodPacket(ReadOnlyContext context, StFloodPacket<?> packet) {

        final SimulationSettings settings = context.getSimulator().getSettings();
        for (int i = 0; i < settings.floodRepeats(); i++) {

            StEvent event = packet.scheduleFor(i);

            if (random.nextDouble() >= settings.lossProbability()) {
                context.getSimulator().scheduleEvent(event);
            }
        }
    }

    private NodeState getNodeState(Node node, int nodesCount) {
        //fixme: implement
        return NodeState.Sleep;
    }

    private void compute(long time, NetGraph netGraph, int floodRepeatCount) {

        final int graphDiameter = netGraph.getDiameter();
        final int graphNodeCount = netGraph.getNodeCount();

        int totalSlotsOfRound = graphDiameter + floodRepeatCount;

        int round = (int) (1.0 * time / totalSlotsOfRound);
        int slot = (int) (time % totalSlotsOfRound);

        int initiatorId = round % graphNodeCount;
        boolean isInitiateSlot = slot == 0;

        Map<Node, NodeState> nodesStateMap = new HashMap<>();
        for (Node node : netGraph.getNodes()) {
            if(node.getId() == initiatorId)
                nodesStateMap.put(node, NodeState.Flood);
        }

        //Note that nodeId start from 1
    }

}
