package ir.ac.kntu.distributedsystems.a2.twopc;

public enum TwoPhaseCommitDecision {
    COMMIT,
    ABORT,
    IN_PROGRESS // The decision has not been made yet.
}
