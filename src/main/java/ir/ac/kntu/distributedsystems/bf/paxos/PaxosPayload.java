package ir.ac.kntu.distributedsystems.bf.paxos;

import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

import java.util.Map;

/**
 * Immutable payload for BlueFlood Paxos-like rounds.
 *
 * @param authorId  node id that generated the message (and owns the votes map)
 * @param proposals all proposals known to the author keyed by proposer id
 * @param votes     known votes per proposal owner id -> (voter id -> vote)
 * @param decisions known decisions per proposal owner id (IN_PROGRESS, COMMIT, ABORT)
 */
public record PaxosPayload(int authorId,
                           Map<Integer, Object> proposals,
                           Map<Integer, Map<Integer, VoteValue>> votes,
                           Map<Integer, PaxosDecision> decisions) {
}
