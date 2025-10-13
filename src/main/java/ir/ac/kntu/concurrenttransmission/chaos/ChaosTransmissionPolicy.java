package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.ConcurrentTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;

public interface ChaosTransmissionPolicy extends ConcurrentTransmissionPolicy {
    int getFinalFloodRepeatCount();

    NodeState getInitialState();

    NodeState getInitialFloodState();

    void endRound(long time);

    int getTotalRounds();

    long getRoundDuration(int round);

}
