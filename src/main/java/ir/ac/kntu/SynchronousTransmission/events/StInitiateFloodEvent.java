package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StEvent;

import java.util.StringJoiner;

public class StInitiateFloodEvent extends StEvent {

    public StInitiateFloodEvent(long time) {
        super(time);
    }

    @Override
    public void handle(ReadOnlyContext context) {
        super.handle(context);

        context.getApplication().onInitiateFlood(context);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StInitiateFloodEvent.class.getSimpleName() + "[", "]")
                .add("t="+getTime())
                .toString();
    }
}
