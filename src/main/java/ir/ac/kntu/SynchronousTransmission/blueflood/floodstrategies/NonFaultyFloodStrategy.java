package ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies;

import ir.ac.kntu.SynchronousTransmission.BlueFloodSettings;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.StMessage;
import ir.ac.kntu.SynchronousTransmission.blueflood.NodeFloodStrategy;
import ir.ac.kntu.SynchronousTransmission.blueflood.TransmissionPolicy;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;

import java.util.List;
import java.util.logging.Logger;

public class NonFaultyFloodStrategy implements NodeFloodStrategy {

    private final Logger logger = Logger.getLogger("NormalFloodStrategy");


    @Override
    public <T> void floodMessage(ContextView context, Node sender, StMessage<T> message) {

        final List<Node> neighbors = context.getNetGraph().getNodeNeighbors(sender);

        for (Node node : neighbors) {
            final FloodPacket<T> stFloodPacket = new FloodPacket<>(context.getTime() + 1, message, sender, node);
            floodPacket(context, stFloodPacket);
        }
    }

    /**
     * Floods the given packet for {@link TransmissionPolicy#getFloodRepeatCount()} times, and
     * also considers {@link BlueFloodSettings#lossProbability()}
     *
     * @param context simulation context
     * @param packet  packet for flooding
     */
    protected void floodPacket(ContextView context, FloodPacket<?> packet) {

        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        for (int i = 0; i < floodRepeatCount; i++) {
            context.getSimulator().schedulePacket(packet.buildPacketWithDelayedSchedule(i));
        }
    }

}

