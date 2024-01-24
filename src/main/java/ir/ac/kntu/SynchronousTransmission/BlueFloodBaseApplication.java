package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.applications.BaseApplication;
import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BlueFloodBaseApplication extends BaseApplication {

    private final static HashMap<Integer, HashSet<Node>> messageToRcvdNodes = new HashMap<>();
    private final Logger logger = Logger.getLogger("BlueFloodBaseApplication");
    private final Random random = new Random(new Date().getTime());

    public abstract StMessage<?> buildMessage(ReadOnlyContext context);

    @Override
    public void onTimeProgress(ReadOnlyContext context) {
        super.onTimeProgress(context);
    }

    @Override
    public void onInitiateFlood(ReadOnlyContext context) {

        final Node node = context.getRoundInitiator();

        final StMessage<?> message = buildMessage(context);
        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" +
                context.getRoundInitiator().getId() + " initiated message [" + message.messageNo() + "]");

        floodMessage(context, node, message);
    }

    @Override
    public void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {

        if (!messageToRcvdNodes.containsKey(packet.getStMessage().messageNo()))
            messageToRcvdNodes.put(packet.getStMessage().messageNo(), new HashSet<>());

        final HashSet<Node> messageReceivers = messageToRcvdNodes.get(packet.getStMessage().messageNo());

        // check to see if a node previously has received the packet
        //  if no, flood it; otherwise stop flooding
        if (!messageReceivers.contains(packet.getReceiver())) {
            messageReceivers.add(packet.getReceiver());

            if (messageReceivers.size() == context.getNetGraph().getNodeCount()) {
                context.getSimulator().roundFinished();
            }
            else {
                floodPacket(context, packet);
            }
        }

        super.onPacketReceive(packet, context);
    }

    protected <T> void floodMessage(ReadOnlyContext context, Node sender, StMessage<T> message) {

        final List<Node> neighbors = context.getNetGraph().getNodeNeighbors(sender);

        for (Node node : neighbors) {
            final StFloodPacket<T> stEvent = new StFloodPacket<>(context.getTime() + 1, message, sender, node);
            floodPacket(context, stEvent);
        }
    }

    private void floodPacket(ReadOnlyContext context, StFloodPacket<?> packet) {

        final SimulationSettings settings = context.getSimulator().getSettings();
        for (int i = 0; i < settings.floodRepeats(); i++) {

            StEvent event = packet.scheduleFor(i);

            if (random.nextDouble() >= settings.lossProbability()) {
                context.getSimulator().scheduleEvent(event);
            }
        }
    }
}
