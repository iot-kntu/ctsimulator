package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.StEvent;

import java.util.StringJoiner;

public class StInitiateFloodEvent extends StEvent {

    private final int nextInitiator;

    public int getNextInitiator() {
        return nextInitiator;
    }

    public StInitiateFloodEvent(long time, int nextInitiator) {
        super(time);
        this.nextInitiator = nextInitiator;
    }

    @Override
    public void handle(ContextView context) {
        super.handle(context);

        context.getApplication().initiateFlood(context);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StInitiateFloodEvent.class.getSimpleName() + "[", "]")
                .add("t="+getTime())
                .toString();
    }
}
