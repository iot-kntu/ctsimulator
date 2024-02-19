package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.CtNetworkTime;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.NodeState;

public interface TransmissionPolicy {

    int getFloodRepeatCount();

    CtNetworkTime getNetworkTime(long time);

    void newRound(CtNetworkTime networkTime, Node initiator);

    void newSlot(int slot);

    void newPacketReceived(Node node, int slot);

    NodeState getNodeState(Node node, int slot);

    int getTotalSlotsOfRound();

    String printHistory();

    void printCurrentNodeStates();
}
