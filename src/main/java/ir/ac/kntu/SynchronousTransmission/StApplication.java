package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.Objects;

/**
 * An interface defining applications on the Synch. Transmission base. Applications can be
 * chained.
 */
public interface StApplication {

    default StApplication next() {
        return new NullApplication();
    }

    void addNextApplication(StApplication application);

    default void onTimeProgress(ReadOnlyContext context) {
        Objects.requireNonNull(context);
        next().onTimeProgress(context);
    }

    default void onInitiateFlood(ReadOnlyContext context) {
        Objects.requireNonNull(context);

        next().onInitiateFlood(context);
    }

    default void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {
        Objects.requireNonNull(packet);
        Objects.requireNonNull(context);

        next().onPacketReceive(packet, context);
    }

}

