package ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.Date;
import java.util.Random;

public abstract class SilentAndFaultyFloodStrategy extends FaultyFloodStrategy {

    private final Random random = new Random(new Date().getTime());
    private double silencePercent = 0.5;

    public double getSilencePercent() {
        return silencePercent;
    }

    public SilentAndFaultyFloodStrategy setSilencePercent(double silencePercent) {
        this.silencePercent = silencePercent;
        return this;
    }

    @Override
    public <T> void floodMessage(ContextView context, Node sender, StMessage<T> message) {
        if (random.nextDouble() > silencePercent)
            super.floodMessage(context, sender, message);

        // else be silent
    }
}
