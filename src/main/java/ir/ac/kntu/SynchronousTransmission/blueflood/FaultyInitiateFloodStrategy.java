package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;

public abstract class FaultyInitiateFloodStrategy extends FaultyFloodStrategy {

    final NonFaultyFloodStrategy nonFaultyFloodStrategy;

    public FaultyInitiateFloodStrategy(BlueFloodSettings settings) {
        super(settings);

        nonFaultyFloodStrategy = new NonFaultyFloodStrategy(settings);
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
