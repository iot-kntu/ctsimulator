package ir.ac.kntu.distributedsystems.a2.twopc;

/**
 * Represents the payload for a 2PC message.
 * It contains the current phase, the proposal, and the final decision.
 */
public record TwoPhaseCommitPayload(
        TwoPhaseCommitPhase phase,
        Object proposal,
        TwoPhaseCommitDecision decision
) {
}