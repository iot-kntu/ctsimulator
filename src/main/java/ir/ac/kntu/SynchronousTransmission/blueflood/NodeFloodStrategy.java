package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;

public interface NodeFloodStrategy {

    double DEFAULT_INTERFERENCE_PROB = 0.9;

    <T> void floodMessage(ContextView context, Node sender, CiMessage<T> message);
}

