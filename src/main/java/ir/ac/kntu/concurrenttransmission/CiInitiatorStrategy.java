package ir.ac.kntu.concurrenttransmission;

/**
 * Constructive Interference message initiation
 * strategy
 */
public interface CiInitiatorStrategy {

    int getCurrentInitiatorId();

    int getNextInitiatorId();
}

