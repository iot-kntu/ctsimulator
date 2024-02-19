package ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.blueflood.NodeFloodStrategy;

public class SilentFloodStrategy implements NodeFloodStrategy {

    @Override
    public <T> void floodMessage(ContextView context, Node sender, CiMessage<T> message) {
        // do nothing
    }

}
