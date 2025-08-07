package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.nodes.LoyalCtNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.List;

/**
 * New applications must implement this interface. Methods of this interface is called from
 * {@link ChaosApplication}.
 */
public interface ChaosNodeListener {

    /**
     * Called when some simultaneous packets are received by a node and
     * just before scheduling of descendant floods
     *
     * @param context        the simulation context
     * @param packets        simultaneous received packets
     * @param selectedPacket the selected packet
     * @param areSimilar     are received packets similar
     * @return tells the application if the received packet should be flooded
     */
    boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket, boolean areSimilar);

    /**
     * Called when some simultaneous packets are lost and not delivered to an intended node
     *
     * @param context           the simulation context
     * @param packets           simultaneous received packets
     * @param arePacketsSimilar determines are the received packets were similar or not. This helps to determine
     *                          the cause of the loss
     */
    void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar);


    /**
     * Called at the beginning of a new round for each node to create its initial message.
     * The implementation should check if the node is the initiator and act accordingly.
     * @param context The simulation context.
     * @param self The node for which the message is being created.
     * @param initiator The designated initiator for this round.
     * @return The initial CtMessage containing a ChaosMessage for the node.
     */
    CtMessage<ChaosMessage> initiateMessage(ContextView context, CtNode self, CtNode initiator);


    /**
     * Called when a node should initiate the round. In {@link LoyalCtNode}s this method is called once independent
     * of the {@link ChaosTransmissionPolicy#getFloodRepeatCount()}.
     *
     * @param context     the simulation context
     * @param initiator   the initiator node
     * @param whichRepeat determines the n_th repeat of flooding based on transmission policy
     * @return the new message
     */
    CtMessage<?> getRoundMessage(ContextView context, CtNode initiator, int whichRepeat);

    /**
     * Merges the given packet into the node message.
     *
     * @param context the simulation context
     * @param receivedPacket  the packet to be merged
     * @return the merged message
     */
    CtMessage<?> merge(ContextView context, FloodPacket<?> receivedPacket);

    /**
     * gets message for flooding in response to receiving a flood message from a neighbor. In loyal nodes
     * this method is not called and the received message is returned, directly.
     * This method is called from non-loyal nodes, and it is possible these nodes
     * return no message, a corrupted message or the same message based on traitorous strategy.
     * It is called after {@link ChaosNodeListener#ctPacketsReceived(ContextView, List, FloodPacket, boolean)} method
     * from {@link ir.ac.kntu.concurrenttransmission.blueflood.nodes.FaultyCtNode} and inside its floodMessage method.
     *
     * @param context         the simulation context
     * @param sender          which neighbor has sent the message
     * @param receivedMessage the full received message
     * @param whichRepeat     determines the n_th repeat of flooding based on transmission policy
     * @return the new message
     */
    CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat);

}
