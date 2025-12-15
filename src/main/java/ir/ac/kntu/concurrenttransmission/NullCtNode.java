package ir.ac.kntu.concurrenttransmission;

/**
 * Lightweight {@link CtNode} implementation used as a harmless placeholder.
 * Any flooding-related calls are intentionally ignored because the null node
 * only exists to satisfy APIs that expect a {@link CtNode} instance.
 */
final class NullCtNode implements CtNode {

    static final NullCtNode INSTANCE = new NullCtNode();

    private NullCtNode() {
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        // no-op
    }

    @Override
    public <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message) {
        // no-op
    }

    @Override
    public int getId() {
        return -1;
    }

    @Override
    public String toString() {
        return "NULL_NODE";
    }
}
