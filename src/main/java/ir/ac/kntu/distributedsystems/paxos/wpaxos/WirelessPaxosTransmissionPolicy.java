package ir.ac.kntu.distributedsystems.paxos.wpaxos;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosDefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.state.PrepareFloodingState;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.state.PrepareListeningState;

public class WirelessPaxosTransmissionPolicy extends ChaosDefaultTransmissionPolicy {

    public WirelessPaxosTransmissionPolicy(int floodRepeatCount, int finalFloodRepeatCount, NetGraph netGraph) {
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
