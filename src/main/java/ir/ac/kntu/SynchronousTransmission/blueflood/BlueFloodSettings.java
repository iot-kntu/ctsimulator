package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.StInitiatorStrategy;

public record BlueFloodSettings(double lossProbability,
                                int maxRounds,
                                StInitiatorStrategy initiatorStrategy,
                                BlueFloodTransmissionPolicy transmissionPolicy) {

}
