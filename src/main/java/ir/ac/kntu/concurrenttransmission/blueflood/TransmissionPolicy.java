package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.ConcurrentTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;

public interface TransmissionPolicy extends ConcurrentTransmissionPolicy {

    void newRound(CtNetworkTime networkTime, CtNode initiator);

    void newSlot(int slot);

    String printHistory();

    void printCurrentNodeStates();

    void newPacketReceived(CtNode node, int slot);
}
