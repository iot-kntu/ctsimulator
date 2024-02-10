package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StEventPriority;

public class SimulationFinishedEvent extends StEvent {

    public SimulationFinishedEvent(long time) {
        super(time, StEventPriority.Low);
    }

    @Override
    public void handle(ContextView context) {
        super.handle(context);

        context.getApplication().simulationFinishing(context);
    }
}
