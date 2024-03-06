package ir.ac.kntu.SynchronousTransmission.blueflood.nodes;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.blueflood.LoyalCtNode;

public class SilentCtNode extends LoyalCtNode {

    public SilentCtNode(int id) {
        super(id);
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CiMessage<T> message) {

    }
}
