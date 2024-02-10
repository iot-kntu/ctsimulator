package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StEventPriority;

public class SimulationTimeProgressesEvent extends StEvent {

    public SimulationTimeProgressesEvent(long time) {
        super(time, StEventPriority.Normal);
    }

    @Override
    public void handle(ContextView context) {
        super.handle(context);

        context.getApplication().simulationTimeProgressed(context);
    }
}
