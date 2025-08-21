package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CtInitiatorStrategy;

public record ChaosStrategies(CtInitiatorStrategy initiatorStrategy,
        ChaosTransmissionPolicy transmissionPolicy) {
}
