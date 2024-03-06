package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.DefaultTransmissionPolicy;
import ir.ac.kntu.SynchronousTransmission.blueflood.TransmissionPolicy;
import ir.ac.kntu.SynchronousTransmission.events.CtPacketsEvent;

/**
 * This class is default null implementation of the {@link ConcurrentTransmissionApplication} interface
 * and does nothing. It can be used as a terminator of an application chain.
 */
public class NullApplication implements ConcurrentTransmissionApplication {


    @Override
    public void simulationStarting(ContextView context) {

    }

    @Override
    public void simulationFinishing(ContextView context) {

    }

    @Override
    public void simulationTimeProgressed(ContextView context) {

    }

    @Override
    public void initiateFlood(ContextView context) {

    }

    @Override
    public void ctPacketsReceived(CtPacketsEvent packets, ContextView context) {

    }

    @Override
    public CtNode getInitiatorNode(ContextView context) {
        return CtNode.NULL_NODE;
    }

    @Override
    public NodeState getNodeState(CtNode node) {
        return NodeState.Sleep;
    }

    @Override
    public TransmissionPolicy getTransmissionPolicy() {
        return new DefaultTransmissionPolicy(0, NetGraph.EMPTY_GRAPH);
    }

    @Override
    public void newRound(ContextView context) {

    }
}
