package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.BaseSimEvent;
import ir.ac.kntu.concurrenttransmission.ContextView;

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

