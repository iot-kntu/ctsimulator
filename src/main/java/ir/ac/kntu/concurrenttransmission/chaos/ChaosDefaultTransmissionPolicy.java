package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.NodeState;
import ir.ac.kntu.concurrenttransmission.FaultAwareTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.FloodingState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.ListeningState;
import ir.ac.kntu.metrics.FaultModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Based on Chaos design, every node in DefaultTransmissionPolicy listens in all
 * slots
 * by default to receive a valid packet. The initiator node state is changed to
 * Flood and starts flooding.
 * Then, receiver nodes receive a valid packet and merge it and flood N
 * consecutive slots.
 */

public class ChaosDefaultTransmissionPolicy implements ChaosTransmissionPolicy, FaultAwareTransmissionPolicy {

    private final int floodRepeatCount;
    private final int finalFloodRepeatCount;
    private final NetGraph netGraph;
    private final List<Long> endRounds;
    private FaultModel faultModel;

    public ChaosDefaultTransmissionPolicy(int floodRepeatCount, int finalFloodRepeatCount, NetGraph netGraph) {
        this.floodRepeatCount = floodRepeatCount;
        this.finalFloodRepeatCount = finalFloodRepeatCount;
        this.netGraph = netGraph;
        this.endRounds = new ArrayList<>();
    }

    @Override
    public int getFloodRepeatCount() {
        return floodRepeatCount;
    }

    @Override
    public int getFinalFloodRepeatCount() {
        return finalFloodRepeatCount;
    }

    @Override
    public ir.ac.kntu.concurrenttransmission.chaos.state.NodeState getInitialState() {
        return new ListeningState();
    }

    @Override
    public ir.ac.kntu.concurrenttransmission.chaos.state.NodeState getInitialFloodState() {
        return new FloodingState();
    }

    @Override
    public CtNetworkTime getNetworkTime(long time) {
        if (endRounds.isEmpty()) {
            return new CtNetworkTime(0, (int) time);
        }

        long prevEnd = 0;
        int index = 0;

        for (long endTime : endRounds) {
            if (time < endTime) {
                long slot = time - prevEnd;
                return new CtNetworkTime(index, (int) slot);
            }
            prevEnd = endTime;
            index++;
        }

        long slot = time - endRounds.get(endRounds.size() - 1);
        return new CtNetworkTime(index, (int) slot);
    }

    @Override
    public void endRound(long time) {
        this.endRounds.add(time);
    }

    @Override
    public int getTotalRounds() {
        return this.endRounds.size();
    }

    @Override
    public long getRoundDuration(int round) {
        if (endRounds.isEmpty()) {
            return -1;
        }
        if (round == 0) {
            return endRounds.get(0);
        }
        if (round > 0 && round < endRounds.size()) {
            return endRounds.get(round) - endRounds.get(round - 1);
        }
        return -1;
    }


    @Override
    public NodeState getNodeState(CtNode node, int slot) {
        return NodeState.Listen;
    }


    @Override
    public int getTotalSlotsOfRound() {
        // With dynamic completion detection, this is now just a maximum bound
        // The actual round will complete when all nodes reach SleepingState or timeout occurs
        return 256; // Maximum slots before timeout
    }

    @Override
    public void setFaultModel(FaultModel faultModel) {
        this.faultModel = faultModel;
    }

    @Override
    public FaultModel getFaultModel() {
        return faultModel;
    }

}
