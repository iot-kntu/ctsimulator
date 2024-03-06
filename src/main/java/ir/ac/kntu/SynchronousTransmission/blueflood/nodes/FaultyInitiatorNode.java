package ir.ac.kntu.SynchronousTransmission.blueflood.nodes;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.NodeState;
import ir.ac.kntu.SynchronousTransmission.blueflood.LoyalCtNode;

public class FaultyInitiatorNode extends LoyalCtNode {
    public FaultyInitiatorNode(int id) {
        super(id);
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CiMessage<T> message) {

        if (context.getApplication().getInitiatorNode(context).equals(sender) &&
                context.getApplication().getNodeState(sender) == NodeState.Flood) {

            super.floodMessage(context, sender, message);
        }
        else {
            super.floodMessage(context, sender, message);
        }
    }

}
