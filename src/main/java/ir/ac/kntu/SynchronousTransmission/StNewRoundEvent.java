package ir.ac.kntu.SynchronousTransmission;

public class StNewRoundEvent extends StEvent {
    public StNewRoundEvent(long time) {
        super(time, StEventPriority.AboveNormal);
    }

    @Override
    public void handle(ContextView context) {
        context.getApplication().newRound(context);
    }
}
