package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.Node;

/**
 * Events that can be received by a receiver and concurrently
 */
public interface CtEvent extends SimEvent {

    Node getReceiver();

    void addPacket(FloodPacket<?> packet);
}
