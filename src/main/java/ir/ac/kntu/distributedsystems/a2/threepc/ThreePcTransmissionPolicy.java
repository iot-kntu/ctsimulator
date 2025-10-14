package ir.ac.kntu.distributedsystems.a2.threepc;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosDefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.distributedsystems.a2.threepc.state.VoteFloodingState;
import ir.ac.kntu.distributedsystems.a2.threepc.state.VoteListeningState;

public class ThreePcTransmissionPolicy extends ChaosDefaultTransmissionPolicy {

    public ThreePcTransmissionPolicy(int floodRepeatCount, int finalFloodRepeatCount, NetGraph netGraph) {
        super(floodRepeatCount, finalFloodRepeatCount, netGraph);
    }

    @Override
    public NodeState getInitialState() {
        return new VoteListeningState();
    }

    @Override
    public NodeState getInitialFloodState() {
        return new VoteFloodingState();
    }
}
