package ir.ac.kntu.concurrenttransmission.nodes;

import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;

import java.util.Objects;

public interface CtNode extends Comparable<CtNode> {

    CtNode NULL_NODE = new LoyalCtNode(-1);

    void initiateFlood(ContextView context, CtNode initiatorNode);

    <T> void floodMessage(ContextView context, CtNode sender, CtMessage<T> message);

    int getId();

    default int compareTo(CtNode o) {
        Objects.requireNonNull(o);
        return this.getId() - o.getId();
    }


}
