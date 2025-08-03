package ir.ac.kntu.concurrenttransmission;

public interface ConcurrentTransmissionPolicy {
    int getFloodRepeatCount();

    CtNetworkTime getNetworkTime(long time);

    NodeState getNodeState(CtNode node, int slot);

    int getTotalSlotsOfRound();

}
