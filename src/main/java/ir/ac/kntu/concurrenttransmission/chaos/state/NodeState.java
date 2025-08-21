package ir.ac.kntu.concurrenttransmission.chaos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

/**
 * An interface representing the behavior of a node in a specific state.
 * This is the core of the State Design Pattern. Each state implementation
 * will define how a node reacts to events.
 */
public interface NodeState {

    /**
     * Handles the event of receiving a packet. The implementation will vary
     * depending on the current state.
     * 
     * @param node           The node that is currently in this state.
     * @param context        The simulation context.
     * @param capturedPacket The packet that was successfully received.
     */
    void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket);

    /**
     * An action to be taken when the node enters this state.
     * 
     * @param node    The node entering this state.
     * @param context The simulation context.
     */
    void onEnter(StatefulNode node, ContextView context);

    /**
     * An action to be taken at the beginning of every time slot
     * while the node remains in this state.
     * 
     * @param node    The node currently in this state.
     * @param context The simulation context.
     */
    void onSlotStart(StatefulNode node, ContextView context);

    /**
     * Returns the symbolic representation of this state.
     * 
     * @return A NodeState enum.
     */
    String toString();
}