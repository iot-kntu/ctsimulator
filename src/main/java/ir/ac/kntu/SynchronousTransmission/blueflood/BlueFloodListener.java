package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;

import java.util.List;

public interface BlueFloodListener {

    /**
     * Called when some simultaneous packets are received by a node
     *
     * @param context the simulation context
     * @param packets simultaneous received packets
     */
    void ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets);

    /**
     * Called when some simultaneous packets are lost and not delivered to an intended node
     *
     * @param context the simulation context
     * @param packets simultaneous received packets
     * @param arePacketsSimilar determines are the received packets were similar or not. This helps to determine
     *                          the cause of the loss
     */
    void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar);

    /**
     * Called when a node should initiate the round. In {@link LoyalCtNode}s this method is called once independent
     * of the {@link TransmissionPolicy#getFloodRepeatCount()}.
     *
     * @param context     the simulation context
     * @param initiator   the initiator node
     * @param whichRepeat determines the n_th repeat of flooding based on transmission policy
     * @return the new message
     */
    CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat);

    /**
     * Receives message for flooding.
     *
     * @param context         the simulation context
     * @param sender          which neighbor has sent the message
     * @param receivedMessage the full received message
     * @param whichRepeat     determines the n_th repeat of flooding based on transmission policy
     * @return the new message
     */
    CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat);


}
