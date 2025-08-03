package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.nodes.CtNode;

/**
 * Events that can be received by a receiver and concurrently
 */
public interface CtEvent extends SimEvent {

    CtNode getReceiver();

    void addPacket(FloodPacket<?> packet);
}
