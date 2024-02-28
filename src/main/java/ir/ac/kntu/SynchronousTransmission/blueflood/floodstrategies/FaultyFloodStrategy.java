package ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.blueflood.NodeFloodStrategy;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;

import java.util.List;
import java.util.logging.Logger;

public abstract class FaultyFloodStrategy implements NodeFloodStrategy {

    private final Logger logger = Logger.getLogger("FaultyFloodStrategy");

    @Override
    public <T> void floodMessage(ContextView context, Node sender, CiMessage<T> message) {

        final List<Node> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        for (int i = 0; i < floodRepeatCount; i++) {

            CiMessage<?> newMessage = createFaultyMessage(context, sender, message, i);

            for (Node neighbor : neighbors) {

                final FloodPacket<?> packet = new FloodPacket<>(context.getTime() + 1 + i,
                                                                newMessage, sender, neighbor);
                context.getSimulator().schedulePacket(packet);
            }
        }
    }

    protected abstract <T> CiMessage<T> createFaultyMessage(ContextView context, Node sender,
                                                            CiMessage<T> message, int whichRepeat);

}

