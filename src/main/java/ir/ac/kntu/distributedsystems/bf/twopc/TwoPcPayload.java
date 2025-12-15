package ir.ac.kntu.distributedsystems.bf.twopc;

import ir.ac.kntu.distributedsystems.a2.twopc.TwoPhaseCommitDecision;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

import java.util.Map;

/**
 * Immutable payload used for BlueFlood based 2PC rounds.
 *
 * @param authorId  node id that generated the message (and owns the votes map)
 * @param proposals all proposals known to the author keyed by proposer id
 * @param votes     author's votes for known proposals keyed by proposal owner id
 * @param decisions author's known final decisions keyed by proposal owner id
 */
public record TwoPcPayload(int authorId,
                           Map<Integer, Object> proposals,
                           Map<Integer, VoteValue> votes,
                           Map<Integer, TwoPhaseCommitDecision> decisions) {
}
