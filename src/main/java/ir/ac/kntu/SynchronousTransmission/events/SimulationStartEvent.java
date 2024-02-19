package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.BaseSimEvent;
import ir.ac.kntu.SynchronousTransmission.ContextView;

import java.util.Objects;

public class SimulationStartEvent extends BaseSimEvent {

    public SimulationStartEvent(long time) {
        super(time, SimEventPriority.High);
    }

    @Override
    public void handle(ContextView context) {
        Objects.requireNonNull(context);

        context.getApplication().simulationStarting(context);
    }
}

