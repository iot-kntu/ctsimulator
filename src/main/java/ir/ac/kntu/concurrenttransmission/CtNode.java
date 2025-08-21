package ir.ac.kntu.concurrenttransmission;

import ir.ac.kntu.concurrenttransmission.blueflood.nodes.LoyalCtNode;

import java.util.Objects;

public interface CtNode extends Comparable<CtNode> {

    CtNode NULL_NODE = new LoyalCtNode(-1);

    void initiateFlood(ContextView context, CtNode initiatorNode);

    // TODO: remove sender
    <T> void floodMessage(ContextView context, CtNode sender, CtMessage<T> message);

    int getId();

    default int compareTo(CtNode o) {
        Objects.requireNonNull(o);
        return this.getId() - o.getId();
    }

}
