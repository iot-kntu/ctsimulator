package ir.ac.kntu.concurrenttransmission.graph;

import ir.ac.kntu.concurrenttransmission.CtNode;

/**
 * Strategy interface for instantiating {@link CtNode} implementations while
 * parsing graph definitions.
 */
public interface CtNodeFactory {

    /**
     * Creates a new {@link CtNode} instance for the given class identifier.
     *
     * @param typeName the identifier specified in the graph source
     * @param nodeId   the numeric id assigned to the node
     * @return a concrete {@link CtNode}
     * @throws IllegalArgumentException when the type identifier is unknown
     *                                  or instantiation fails
     */
    CtNode create(String typeName, int nodeId);
}
