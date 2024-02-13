package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.StMessage;

public class SilentFloodStrategy implements FloodStrategy {

    @Override
    public <T> void floodMessage(ContextView context, Node sender, StMessage<T> message) {
        // do nothing
    }
}
