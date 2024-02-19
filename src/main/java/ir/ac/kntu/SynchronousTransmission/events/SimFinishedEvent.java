package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.BaseSimEvent;
import ir.ac.kntu.SynchronousTransmission.ContextView;

import java.util.Objects;

public class SimFinishedEvent extends BaseSimEvent {

    public SimFinishedEvent(long time) {
        super(time, SimEventPriority.Low);
    }

    @Override
    public void handle(ContextView context) {
        Objects.requireNonNull(context);

        context.getApplication().simulationFinishing(context);
    }
}
