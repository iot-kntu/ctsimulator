package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.CtNetworkTime;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.NodeState;

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
