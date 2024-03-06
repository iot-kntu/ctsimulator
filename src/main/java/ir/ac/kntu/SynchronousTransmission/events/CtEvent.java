package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.CtNode;

/**
 * Events that can be received by a receiver and concurrently
 */
public interface CtEvent extends SimEvent {

    CtNode getReceiver();

    void addPacket(FloodPacket<?> packet);
}
