package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

/**
 * This class is the default implementation of {@link StApplication} interface
 * and does nothing. It can be overriden and used for logging.
 */
public class NopStApplication implements StApplication {

    // TODO: 1/23/24 add listener to enable simple logging
    @Override
    public StMessage<String> initiateFlood(ReadOnlyContext context) {
        return new StMessage<>(context.getRoundInitiator(), "");
    }

    @Override
    public void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {

    }
}
