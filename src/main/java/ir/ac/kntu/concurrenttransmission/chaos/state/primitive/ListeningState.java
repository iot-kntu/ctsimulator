package ir.ac.kntu.concurrenttransmission.chaos.state.primitive;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

/**
 * Behavior of a node when it is in the Listening state.
 * In this state, the node processes incoming packets and decides whether to
 * transition
 * to a flooding state.
 */
public class ListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();

        // Use the listener to perform the application-specific merge logic.
        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(mergedMessage);

        // Check for completion first.
        int totalNodes = context.getNetGraph().getNodeCount();
        if (mergedMessage.content().flags().getParticipationCount() == totalNodes) {
            node.setState(new FinalFloodingState(), context);
            return;
        }

        // Then, check if the node should flood based on Chaos rules.
        if (node.shouldFlood(currentKnowledge, (CtMessage<ChaosMessage>) capturedPacket.ctMessage())) {
            node.setState(new FloodingState(), context);
        }
        // Otherwise, the node remains in the ListeningState, waiting for more packets.
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // Nothing to do when entering listen state.
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {

    }

    @Override
    public boolean isListening() {
        return true;
    }

    @Override
    public String toString() {
        return "R";
    }
}
