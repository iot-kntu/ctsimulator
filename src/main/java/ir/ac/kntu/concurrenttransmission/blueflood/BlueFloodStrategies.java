package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.CiInitiatorStrategy;

public record BlueFloodStrategies(CiInitiatorStrategy initiatorStrategy,
                                  TransmissionPolicy transmissionPolicy) {

}
