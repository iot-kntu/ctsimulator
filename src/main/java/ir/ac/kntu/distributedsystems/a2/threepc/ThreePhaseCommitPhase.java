package ir.ac.kntu.distributedsystems.a2.threepc;

public enum ThreePhaseCommitPhase {
    VOTING,      // Phase 1: Coordinator is proposing and participants are voting.
    PRE_COMMIT,  // Phase 2: Coordinator sends pre-commit to participants.
    FINALIZING   // Phase 3: Coordinator is disseminating the final COMMIT or ABORT decision.
}