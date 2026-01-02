package ir.ac.kntu.distributedsystems.bf.tom;

import java.util.List;
import java.util.Set;

/**
 * Immutable payload for BlueFlood total order multicast.
 *
 * @param entries         delta messages (current round + replies)
 * @param missingRequests missing sequences requested by sender
 */
public record TotalOrderPayload(List<TotalOrderMessage> entries, Set<Integer> missingRequests) {
}
