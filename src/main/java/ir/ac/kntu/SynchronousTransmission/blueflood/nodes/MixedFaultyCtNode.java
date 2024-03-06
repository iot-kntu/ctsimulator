package ir.ac.kntu.SynchronousTransmission.blueflood.nodes;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;

import java.util.Date;
import java.util.Random;

/**
 * This node type somtimes sends faulty messages and sometimes remains silent
 * based on silencePercent parameter
 */
public class MixedFaultyCtNode extends FaultyCtNode {

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
            super.floodMessage(context, sender, message);
        }
        // else remain silent
    }


}
