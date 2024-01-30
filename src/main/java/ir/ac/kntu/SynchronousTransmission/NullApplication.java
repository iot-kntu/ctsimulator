package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

/**
 * This class is default null implementation of the {@link StApplication} interface
 * and does nothing. It can be used as a terminator of an application chain.
 */
public class NullApplication implements StApplication {

    @Override
    public void addNextApplication(StApplication application) {

    }

    @Override
    public void onTimeProgress(ReadOnlyContext context) {

    }

    @Override
    public void onInitiateFlood(ReadOnlyContext context) {

    }

    @Override
    public void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {

    }
}
