package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.ConcurrentTransmissionApplication;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;

public interface CtChaosApplication extends ConcurrentTransmissionApplication {
    ChaosNodeListener getChaosNodeListener(CtNode node);

    void checkRoundCompletion(ContextView context);
}
