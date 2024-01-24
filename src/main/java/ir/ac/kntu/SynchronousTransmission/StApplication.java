package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.Objects;

/**
 * An interface defining applications on the Synch. Transmission base. Applications can be
 * chained.
 */
public interface StApplication {

    default void onTimeProgress(ReadOnlyContext context) {
    }

    default StMessage<?> onInitiateFlood(ReadOnlyContext context) {

        Objects.requireNonNull(context);
        return new StMessage<>(context.getRoundInitiator(), "");
    }

    default void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {

    }

}

