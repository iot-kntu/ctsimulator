package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;

// TODO: 2/19/24 In future we may have different types of packet,
//  which may necessitates to define a packet interface

/**
 * Represents a data sent between two adjacent nodes
 */
public record FloodPacket<T>(long time, CiMessage<T> ciMessage, CtNode sender, CtNode receiver) {

    @Override
    public String toString() {
        return String.format("StFloodPacket[%d:%s->%s]=[%s]", time(), sender, receiver, ciMessage);
    }

    public FloodPacket<T> buildPacketWithDelayedSchedule(int delay) {
        if (delay < 0)
            throw new IllegalArgumentException("delay must be 0 or a positive number");
        if (delay == 0)
            return this;

        return new FloodPacket<>(time() + delay, ciMessage(), sender(), receiver());
    }
}

