package ir.ac.kntu.concurrenttransmission.blueflood.nodes;

import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;

import java.util.Date;
import java.util.Random;

/**
 * This node type somtimes sends faulty messages and sometimes remains silent
 * based on silencePercent parameter. The default silence probability is 50%
 */
public class MixedFaultyCtNode extends FaultyCtNode {

    private final Random random = new Random(new Date().getTime());
    private double silencePercent = 0.5;

    public MixedFaultyCtNode(Integer id) {
        super(id);
    }

    public double getSilencePercent() {
        return silencePercent;
    }

    public void setSilencePercent(double silencePercent) {
        this.silencePercent = silencePercent;
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        if (random.nextDouble() > silencePercent) {
            super.initiateFlood(context, initiatorNode);
        }
        // else remain silent
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CiMessage<T> message) {

        if (random.nextDouble() > silencePercent) {
            super.floodMessage(context, sender, message);
        }
        // else remain silent
    }


}
