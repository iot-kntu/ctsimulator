package ir.ac.kntu.concurrenttransmission.chaos;

/**
 * An interface representing the state of a single node's "flag" within a Chaos message.
 * This allows for different types of flags (e.g., simple participation, multi-value votes).
 * Implementations must be immutable.
 */
public interface Flag {
    /**
     * Merges this flag with another flag. The logic depends on the flag type.
     * For example, a participation flag would use OR, while a vote flag might
     * keep the first non-undecided value.
     * @param other The other flag to merge with.
     * @return A new, merged NodeFlag instance.
     */
    Flag merge(Flag other);
}