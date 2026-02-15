package ir.ac.kntu.distributedsystems.a2.wmultipaxos;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosDefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.distributedsystems.a2.wmultipaxos.state.PrepareFloodingState;
import ir.ac.kntu.distributedsystems.a2.wmultipaxos.state.PrepareListeningState;

public class WirelessMultiPaxosTransmissionPolicy extends ChaosDefaultTransmissionPolicy {

    public WirelessMultiPaxosTransmissionPolicy(int floodRepeatCount, int finalFloodRepeatCount, NetGraph netGraph) {
        super(floodRepeatCount, finalFloodRepeatCount, netGraph);
    }

    @Override
    public NodeState getInitialState() {
        return new PrepareListeningState();
    }

    @Override
    public NodeState getInitialFloodState() {
        return new PrepareFloodingState();
    }
}
