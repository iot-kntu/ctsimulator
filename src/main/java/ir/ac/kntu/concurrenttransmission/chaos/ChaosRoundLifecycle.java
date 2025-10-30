package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.FinalFloodingState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.SleepingState;

class ChaosRoundLifecycle {

    private final ChaosTransmissionPolicy transmissionPolicy;
    private long roundStartTime;
    private boolean roundCompleted;
    private int nodesInFinalFlooding;
    private int nodesInSleeping;

    ChaosRoundLifecycle(ChaosTransmissionPolicy transmissionPolicy) {
        this.transmissionPolicy = transmissionPolicy;
        reset(0);
    }

    void reset(long startTime) {
        this.roundStartTime = startTime;
        this.roundCompleted = false;
        this.nodesInFinalFlooding = 0;
        this.nodesInSleeping = 0;
    }

    void onNodeStateChanged(NodeState oldState, NodeState newState) {
        if (oldState instanceof FinalFloodingState) {
            nodesInFinalFlooding--;
        } else if (oldState instanceof SleepingState) {
            nodesInSleeping--;
        }

        if (newState instanceof FinalFloodingState) {
            nodesInFinalFlooding++;
        } else if (newState instanceof SleepingState) {
            nodesInSleeping++;
        }
    }

    boolean isRoundCompleted() {
        return roundCompleted;
    }

    boolean shouldTimeout(long currentTime) {
        return !roundCompleted && (currentTime - roundStartTime) >= transmissionPolicy.getTotalSlotsOfRound();
    }

    boolean shouldCompleteBySleeping(int totalNodes) {
        return !roundCompleted && nodesInSleeping == totalNodes;
    }

    void markCompleted() {
        this.roundCompleted = true;
    }

    long elapsedSlots(long currentTime) {
        return currentTime - roundStartTime;
    }
}
