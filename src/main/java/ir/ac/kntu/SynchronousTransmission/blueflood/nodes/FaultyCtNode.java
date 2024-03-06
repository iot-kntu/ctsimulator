package ir.ac.kntu.SynchronousTransmission.blueflood.nodes;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.blueflood.LoyalCtNode;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;

import java.util.List;

/**
 * Represents a faulty node.
 */
public class FaultyCtNode extends LoyalCtNode {
    public FaultyCtNode(int id) {
        super(id);
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CiMessage<T> message) {


        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        for (int i = 0; i < floodRepeatCount; i++) {

            CiMessage<?> newMessage = createFaultyMessage(context, sender, message, i);

            for (CtNode neighbor : neighbors) {

                final FloodPacket<?> packet = new FloodPacket<>(context.getTime() + 1 + i,
                                                                newMessage, sender, neighbor);
                context.getSimulator().schedulePacket(packet);
            }
        }
    }

    protected <T> CiMessage<?> createFaultyMessage(ContextView context, CtNode sender,
                                                   CiMessage<T> receivedMessage, int whichRepeat) {
        return CiMessage.NULL_MESSAGE;
    }

}
