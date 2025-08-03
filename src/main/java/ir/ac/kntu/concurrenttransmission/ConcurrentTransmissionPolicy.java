package ir.ac.kntu.concurrenttransmission;

import ir.ac.kntu.concurrenttransmission.nodes.CtNode;

public interface ConcurrentTransmissionPolicy {
    int getFloodRepeatCount();

    CtNetworkTime getNetworkTime(long time);

    NodeState getNodeState(CtNode node, int slot);

    int getTotalSlotsOfRound();

}
