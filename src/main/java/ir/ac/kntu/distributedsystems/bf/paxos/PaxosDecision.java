package ir.ac.kntu.distributedsystems.bf.paxos;

/**
 * Simple decision states for BlueFlood Paxos-like flow.
 */
public enum PaxosDecision {
    IN_PROGRESS,
    COMMIT,
    ABORT
}
