package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StEventPriority;

public class SimulationStartEvent extends StEvent {

    public SimulationStartEvent(long time) {
        super(time, StEventPriority.High);
    }

    @Override
    public void handle(ContextView context) {
        super.handle(context);

        context.getApplication().simulationStarting(context);
    }
}

