package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.BlueFloodMessageBuilder;
import ir.ac.kntu.SynchronousTransmission.CiInitiatorStrategy;

public record BlueFloodStrategies(CiInitiatorStrategy initiatorStrategy,
                                  TransmissionPolicy transmissionPolicy,
                                  BlueFloodMessageBuilder<?> messageBuilder) {

}
