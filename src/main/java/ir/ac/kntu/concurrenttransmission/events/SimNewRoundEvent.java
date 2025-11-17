package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.BaseSimEvent;
import ir.ac.kntu.concurrenttransmission.ContextView;

public class SimNewRoundEvent extends BaseSimEvent {
    public SimNewRoundEvent(long time) {
        super(time, SimEventPriority.AboveNormal);
    }

    @Override
    public void handle(ContextView context) {
        context.getApplication().newRound(context);
    }
}
