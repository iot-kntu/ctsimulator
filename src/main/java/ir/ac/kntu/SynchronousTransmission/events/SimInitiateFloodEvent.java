package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.BaseSimEvent;
import ir.ac.kntu.SynchronousTransmission.ContextView;

import java.util.Objects;
import java.util.StringJoiner;

public class SimInitiateFloodEvent extends BaseSimEvent {

    private final int nextInitiator;

    public int getNextInitiator() {
        return nextInitiator;
    }

    public SimInitiateFloodEvent(long time, int nextInitiator) {
        super(time);
        this.nextInitiator = nextInitiator;
    }

    @Override
    public void handle(ContextView context) {
        Objects.requireNonNull(context);

        context.getApplication().initiateFlood(context);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SimInitiateFloodEvent.class.getSimpleName() + "[", "]")
                .add("t="+getTime())
                .toString();
    }
}
