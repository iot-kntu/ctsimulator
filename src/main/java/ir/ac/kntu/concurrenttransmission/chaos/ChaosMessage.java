package ir.ac.kntu.concurrenttransmission.chaos;

/**
 * Represents the message content for Chaos protocol, containing flags and a
 * payload.
 *
 * @param flags   A bitset where each bit represents a node's participation.
 * @param payload The aggregated data (e.g., the maximum value).
 */
public record ChaosMessage(FlagField flags, Object payload) {

}