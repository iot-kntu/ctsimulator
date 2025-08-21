package ir.ac.kntu.distributedsystems.a2.twopc;

public enum TwoPhaseCommitPhase {
    VOTING,      // Phase 1: Coordinator is proposing and participants are voting.
    FINALIZING   // Phase 2: Coordinator is disseminating the final COMMIT or ABORT decision.
}