package ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.NodeState;
import ir.ac.kntu.SynchronousTransmission.StMessage;

public abstract class FaultyInitiateFloodStrategy extends FaultyFloodStrategy {

    final NonFaultyFloodStrategy nonFaultyFloodStrategy;

    public FaultyInitiateFloodStrategy() {
        nonFaultyFloodStrategy = new NonFaultyFloodStrategy();
    }

    @Override
    public <T> void floodMessage(ContextView context, Node sender, StMessage<T> message) {

        if (context.getApplication().getInitiatorNode(context).equals(sender) &&
                context.getApplication().getNodeState(sender) == NodeState.Flood) {

            super.floodMessage(context, sender, message);
        }
        else {
            nonFaultyFloodStrategy.floodMessage(context, sender, message);
        }
    }
}
