package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.NodeState;

public interface TransmissionPolicy {

    int getFloodRepeatCount();

    CtNetworkTime getNetworkTime(long time);

    void newRound(CtNetworkTime networkTime, CtNode initiator);

    void newSlot(int slot);

    void newPacketReceived(CtNode node, int slot);

    NodeState getNodeState(CtNode node, int slot);

    int getTotalSlotsOfRound();

    String printHistory();

    void printCurrentNodeStates();
}
