package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.BaseSimEvent;
import ir.ac.kntu.concurrenttransmission.ContextView;

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
