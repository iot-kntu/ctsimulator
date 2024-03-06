package ir.ac.kntu.concurrenttransmission;

import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

public class SimNewRoundEvent extends BaseSimEvent {
    public SimNewRoundEvent(long time) {
        super(time, SimEventPriority.AboveNormal);
    }

    @Override
    public void handle(ContextView context) {
        context.getApplication().newRound(context);
    }
}
