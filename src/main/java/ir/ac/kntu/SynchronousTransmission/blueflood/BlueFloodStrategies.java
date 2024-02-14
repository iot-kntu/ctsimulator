package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.StInitiatorStrategy;

public record BlueFloodStrategies(StInitiatorStrategy initiatorStrategy,
                                  TransmissionPolicy transmissionPolicy) {

}
