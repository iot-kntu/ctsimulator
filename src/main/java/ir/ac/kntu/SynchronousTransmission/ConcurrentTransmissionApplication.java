package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.TransmissionPolicy;
import ir.ac.kntu.SynchronousTransmission.events.CtPacketsEvent;

/**
 * An interface defining applications on the Synch. Transmission base. Applications can be
 * chained.
 */
public interface ConcurrentTransmissionApplication {

    /**
     * Called by simulator to announce simulation start
     *
     * @param context
     */
    void simulationStarting(ContextView context);

    void simulationFinishing(ContextView context);

    /**
     * Called when simulation time progresses by one unit
     *
     * @param context
     */
    void simulationTimeProgressed(ContextView context);

    void newRound(ContextView context);

    void initiateFlood(ContextView context) throws RuntimeException;

    void ctPacketsReceived(CtPacketsEvent ctEvent, ContextView context);

    CtNode getInitiatorNode(ContextView context);

    NodeState getNodeState(CtNode node);

    TransmissionPolicy getTransmissionPolicy();
}

