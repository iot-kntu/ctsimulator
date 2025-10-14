package ir.ac.kntu.distributedsystems.a2.threepc;

/**
 * Represents the payload for a 3PC message.
 * It contains the current phase, the proposal, and the final decision.
 */
public record ThreePhaseCommitPayload(
        ThreePhaseCommitPhase phase,
        Object proposal,
        ThreePhaseCommitDecision decision
) {
}