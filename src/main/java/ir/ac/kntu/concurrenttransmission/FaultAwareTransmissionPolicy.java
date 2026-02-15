package ir.ac.kntu.concurrenttransmission;

import ir.ac.kntu.metrics.FaultModel;

public interface FaultAwareTransmissionPolicy {
    void setFaultModel(FaultModel faultModel);

    FaultModel getFaultModel();
}
