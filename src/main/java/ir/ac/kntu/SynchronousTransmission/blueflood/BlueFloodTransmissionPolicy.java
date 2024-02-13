package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.NodeState;
import ir.ac.kntu.SynchronousTransmission.StNetworkTime;

public interface BlueFloodTransmissionPolicy {

    int getFloodRepeatCount();

    StNetworkTime getNetworkTime(long time);

    void newRound(StNetworkTime networkTime, Node initiator);

    void newSlot(int slot);

    void newPacketReceived(Node node, int slot);

    NodeState getNodeState(Node node, int slot);

    int getTotalSlotsOfRound();

    String printHistory();

    void printCurrentNodeStates();
}
