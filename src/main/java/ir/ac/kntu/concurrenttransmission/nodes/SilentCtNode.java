package ir.ac.kntu.concurrenttransmission.nodes;

import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;

public class SilentCtNode extends LoyalCtNode {

    public SilentCtNode(Integer id) {
        super(id);
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {

    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CtMessage<T> message) {

    }
}
