package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

/**
 * An interface defining applications on the Synch. Transmission base. Applications can be
 * chained.
 */
public interface StApplication {

    default StApplication next() {
        return new NullApplication();
    }

    void addNextApplication(StApplication application);

    void onTimeProgress(ReadOnlyContext context);

    void onInitiateFlood(ReadOnlyContext context);

    void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context);
}

