package ir.ac.kntu.distributedsystems.bf.threepc;

import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommitDecision;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

import java.util.Map;

/**
 * Immutable payload for BlueFlood-based 3PC rounds.
 *
 * @param authorId   node id that generated the message (and owns the votes map)
 * @param proposals  all proposals known to the author keyed by proposer id
 * @param votes      known votes per proposal owner id -> (voter id -> vote)
 * @param decisions  author's known decisions per proposal owner id (IN_PROGRESS, PRE_COMMIT, COMMIT, ABORT)
 */
public record ThreePcPayload(int authorId,
                             Map<Integer, Object> proposals,
                             Map<Integer, Map<Integer, VoteValue>> votes,
                             Map<Integer, ThreePhaseCommitDecision> decisions) {
}
