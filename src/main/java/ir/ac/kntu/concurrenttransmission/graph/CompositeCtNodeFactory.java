package ir.ac.kntu.concurrenttransmission.graph;

import ir.ac.kntu.concurrenttransmission.CtNode;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Tries a list of factories until one can create the requested node type.
 */
public class CompositeCtNodeFactory implements CtNodeFactory {

    private final List<CtNodeFactory> delegates;

    public CompositeCtNodeFactory(List<CtNodeFactory> delegates) {
        Objects.requireNonNull(delegates);
        if (delegates.isEmpty()) {
            throw new IllegalArgumentException("CompositeCtNodeFactory requires at least one delegate");
        }
        this.delegates = List.copyOf(delegates);
    }

    public CompositeCtNodeFactory(CtNodeFactory... delegates) {
        this(Arrays.asList(delegates));
    }

    @Override
    public CtNode create(String typeName, int nodeId) {
        IllegalArgumentException lastFailure = null;
        for (CtNodeFactory factory : delegates) {
            try {
                return factory.create(typeName, nodeId);
            } catch (IllegalArgumentException ex) {
                lastFailure = ex;
            }
        }
        throw (lastFailure != null) ? lastFailure
                : new IllegalArgumentException("Cannot create node of type '" + typeName + "'");
    }
}
