package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StEvent;

public class StInitiateFloodEvent extends StEvent {

    public StInitiateFloodEvent(long time) {
        super(time);
    }

    @Override
    public void handle(ReadOnlyContext context) {
        super.handle(context);

        context.getApplication().onInitiateFlood(context);
    }
}
