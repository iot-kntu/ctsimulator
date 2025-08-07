package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.NodeState;

/**
 * Based on Chaos design, every node in DefaultTransmissionPolicy listens in all slots
 * by default to receive a valid packet. The initiator node state is changed to Flood and starts flooding.
 * Then, receiver nodes receive a valid packet and merge it and flood N consecutive slots.
 */
public class ChaosDefaultTransmissionPolicy implements ChaosTransmissionPolicy {

    private final int floodRepeatCount;
    private final int finalFloodRepeatCount;
    private final NetGraph netGraph;

    public ChaosDefaultTransmissionPolicy(int floodRepeatCount, int finalFloodRepeatCount, NetGraph netGraph) {
        this.floodRepeatCount = floodRepeatCount;
        this.finalFloodRepeatCount = finalFloodRepeatCount;
        this.netGraph = netGraph;
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
    public CtNetworkTime getNetworkTime(long time) {
        final int totalSlotsOfRound = getTotalSlotsOfRound();
        int round = (int) (1.0 * time / totalSlotsOfRound);
        int slot = (int) (time % totalSlotsOfRound);

        return new CtNetworkTime(round, slot);
    }



    @Override
    public NodeState getNodeState(CtNode node, int slot) {
        return NodeState.Listen;

    }

    @Override
    public int getTotalSlotsOfRound() {
        return netGraph.getNodeCount() + 2 * netGraph.getDiameter() + floodRepeatCount + finalFloodRepeatCount + 1; // TODO: fix it
    }

}
