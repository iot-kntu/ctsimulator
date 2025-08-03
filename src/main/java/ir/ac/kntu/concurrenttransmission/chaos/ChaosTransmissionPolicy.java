package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.ConcurrentTransmissionPolicy;

public interface ChaosTransmissionPolicy extends ConcurrentTransmissionPolicy {
    int getFinalFloodRepeatCount();
}
