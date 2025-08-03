package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CiInitiatorStrategy;

public record ChaosStrategies(CiInitiatorStrategy initiatorStrategy,
                              ChaosTransmissionPolicy transmissionPolicy) {
}
