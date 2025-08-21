package ir.ac.kntu.distributedsystems.a2.twopc;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosDefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.distributedsystems.a2.twopc.state.VoteFloodingState;
import ir.ac.kntu.distributedsystems.a2.twopc.state.VoteListeningState;

public class TwoPcTransmissionPolicy extends ChaosDefaultTransmissionPolicy {

    public TwoPcTransmissionPolicy(int floodRepeatCount, int finalFloodRepeatCount, NetGraph netGraph) {
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
