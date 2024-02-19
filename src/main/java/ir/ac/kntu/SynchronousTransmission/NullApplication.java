package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.DefaultTransmissionPolicy;
import ir.ac.kntu.SynchronousTransmission.blueflood.TransmissionPolicy;
import ir.ac.kntu.SynchronousTransmission.events.CtPacketsEvent;

/**
 * This class is default null implementation of the {@link StApplication} interface
 * and does nothing. It can be used as a terminator of an application chain.
 */
public class NullApplication implements StApplication {


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
    public Node getInitiatorNode(ContextView context) {
        return Node.NULL_NODE;
    }

    @Override
    public NodeState getNodeState(Node node) {
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
