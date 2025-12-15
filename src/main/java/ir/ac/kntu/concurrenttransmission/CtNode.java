package ir.ac.kntu.concurrenttransmission;

import java.util.Objects;

public interface CtNode extends Comparable<CtNode> {

    CtNode NULL_NODE = NullCtNode.INSTANCE;

    void initiateFlood(ContextView context, CtNode initiatorNode);

    // TODO: remove sender
    <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message);

    int getId();

    default int compareTo(CtNode o) {
        Objects.requireNonNull(o);
        return this.getId() - o.getId();
    }

}
