package ir.ac.kntu.SynchronousTransmission.blueflood.nodes;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.blueflood.LoyalCtNode;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;

import java.util.Date;
import java.util.List;
import java.util.Random;

public class MixedFaultyCtNode extends LoyalCtNode {

    private final Random random = new Random(new Date().getTime());
    private double silencePercent = 0.5;

    public MixedFaultyCtNode(int id) {
        super(id);
    }

    public double getSilencePercent() {
        return silencePercent;
    }

    public void setSilencePercent(double silencePercent) {
        this.silencePercent = silencePercent;
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CiMessage<T> message) {

        if (random.nextDouble() > silencePercent) {
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
        // else remain silent
    }

    protected <T> CiMessage<?> createFaultyMessage(ContextView context, CtNode sender, CiMessage<T> receivedMessage,
                                                   int whichRepeat) {
        return CiMessage.NULL_MESSAGE;
    }

}
