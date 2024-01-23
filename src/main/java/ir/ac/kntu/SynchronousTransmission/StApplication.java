package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

public interface StApplication {

    StMessage<?> initiateFlood(ReadOnlyContext context);

    void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context);
}

