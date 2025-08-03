package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CiMessage;
import java.util.BitSet;

/**
 * Represents the message content for Chaos protocol, containing flags and a payload.
 * @param flags A bitset where each bit represents a node's participation.
 * @param payload The aggregated data (e.g., the maximum value).
 */
public record ChaosMessage(BitSet flags, Object payload) {

    /**
     * Creates an initial empty message for a node.
     * @param nodeId The ID of the node creating the message.
     * @param nodeCount Total number of nodes in the network.
     * @param initialPayload The initial data payload for this node.
     * @return A new ChaosMessage.
     */
    public static ChaosMessage createInitial(int nodeId, int nodeCount, Object initialPayload) {
        BitSet initialFlags = new BitSet(nodeCount);
        initialFlags.set(nodeId);
        return new ChaosMessage(initialFlags, initialPayload);
    }
}