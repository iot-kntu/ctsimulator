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

    /**
     * Receives message for flooding. It is expected the application relay this call to listeners.
     * @param context the simulation context
     * @param sender which neighbor has sent the message
     * @param receivedMessage the full received message, if this argument is {@link CiMessage#NULL_MESSAGE} then
     *                        flooding is called for message initiation
     * @param whichRepeat determines the nth repeat of flooding based on transmission policy
     * @return the new message
     */
    CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat);

    CiMessage<?> getRoundInitiationMessage(ContextView context, CtNode initiator, int whichRepeat);
}

