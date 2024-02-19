package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.SimEventPriority;

public class SimNewRoundEvent extends BaseSimEvent {
    public SimNewRoundEvent(long time) {
        super(time, SimEventPriority.AboveNormal);
    }

    @Override
    public void handle(ContextView context) {
        context.getApplication().newRound(context);
    }
}
