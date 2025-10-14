package ir.ac.kntu.distributedsystems.a2.threepc;

public enum ThreePhaseCommitDecision {
    COMMIT,
    ABORT,
    PRE_COMMIT, // For the pre-commit phase
    IN_PROGRESS // The decision has not been made yet.
}