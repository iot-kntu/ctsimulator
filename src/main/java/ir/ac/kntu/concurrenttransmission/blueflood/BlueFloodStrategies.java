package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.CtInitiatorStrategy;

public record BlueFloodStrategies(CtInitiatorStrategy initiatorStrategy,
                                  TransmissionPolicy transmissionPolicy) {

}
