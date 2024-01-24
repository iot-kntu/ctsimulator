package ir.ac.kntu.SynchronousTransmission.applications;

import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;

import java.util.Objects;

public class LimitedRoundStApplication extends BaseApplication {

    private final int roundsLimit;

    public LimitedRoundStApplication(int roundsLimit) {
        this.roundsLimit = roundsLimit;
    }

    public int getRoundsLimit() {
        return roundsLimit;
    }

    @Override
    public void onInitiateFlood(ReadOnlyContext context) {
        Objects.requireNonNull(context);

        if (context.getRound() < this.roundsLimit)
            next().onInitiateFlood(context);

        // else do nothing, and return
    }

}
