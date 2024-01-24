package ir.ac.kntu.SynchronousTransmission.applications;

import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StApplication;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.Objects;

public class LimitedRoundStApplication implements StApplication {

    private int roundsLimit = 10;

    public LimitedRoundStApplication(int roundsLimit) {
        this.roundsLimit = roundsLimit;
    }

    @Override
    public StMessage<?> onInitiateFlood(ReadOnlyContext context) {
        Objects.requireNonNull(context);

        if(context.getRound() < this.roundsLimit)
            return new StMessage<>(context.getRoundInitiator(), new Object());
        else
            return StMessage.NULL_MESSAGE;
    }

}
