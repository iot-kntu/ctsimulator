package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.List;

public interface ReadOnlyContext {

    StSimulator getSimulator();

    List<StApplication> getApplications();

    long getTime();

    int getRound();

    int getSlot();

    NetGraph getNetGraph();

    Node getRoundInitiator();

    StMessage<?> initiateFlood(ReadOnlyContext context);

    void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context);
}
