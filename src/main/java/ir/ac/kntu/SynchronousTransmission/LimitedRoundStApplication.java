package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

public class LimitedRoundStApplication implements StApplication {

    private int roundsLimit = 10;

    public LimitedRoundStApplication(int roundsLimit) {
        this.roundsLimit = roundsLimit;
    }

    @Override
    public StMessage<?> onInitiateFlood(ReadOnlyContext context) {
        if(context.getRound() < this.roundsLimit)
            return new StMessage<>(context.getRoundInitiator(), new Object());
        else
            return StMessage.NULL_MESSAGE;
    }

    @Override
    public void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {

    }
}
