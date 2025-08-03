package ir.ac.kntu.concurrenttransmission;

/**
 *  Message initiation
 * strategy
 */
public interface CtInitiatorStrategy {

    int getCurrentInitiatorId();

    int getNextInitiatorId();
}

