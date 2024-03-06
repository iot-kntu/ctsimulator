package ir.ac.kntu.SynchronousTransmission.blueflood.nodes;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.blueflood.LoyalCtNode;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;

import java.util.List;
import java.util.Objects;

/**
 * Represents a complete faulty node. The faulty node may generate faulty messages
 * in round initiation, or in flooding slots.
 */
public class FaultyCtNode extends LoyalCtNode {

    public FaultyCtNode(int id) {
        super(id);
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(initiatorNode);

        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(initiatorNode);

        for (CtNode node : neighbors) {
            for (int repeat = 0; repeat < floodRepeatCount; repeat++) {

                final CiMessage<?> ciMessage = context
                        .getApplication()
                        .getRoundInitiationMessage(context, initiatorNode, repeat);

                final FloodPacket<?> stFloodPacket = new FloodPacket<>(context.getTime() + repeat, ciMessage,
                                                                       initiatorNode, node);
                context.getSimulator().schedulePacket(stFloodPacket);
            }
        }

    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CiMessage<T> message) {

        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        for (int i = 0; i < floodRepeatCount; i++) {

            CiMessage<?> newMessage = context.getApplication().getMessage(context, sender, message, i);

            for (CtNode neighbor : neighbors) {
                final FloodPacket<?> packet = new FloodPacket<>(context.getTime() + 1 + i,
                                                                newMessage, sender, neighbor);
                context.getSimulator().schedulePacket(packet);
            }
        }
    }

}
