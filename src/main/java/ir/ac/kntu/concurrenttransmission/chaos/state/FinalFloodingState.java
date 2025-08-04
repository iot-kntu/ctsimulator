package ir.ac.kntu.concurrenttransmission.chaos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.NodeStateBehavior;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

/**
 * Behavior of a node when it has reached completion and enters the final flood phase.
 */
public class FinalFloodingState implements NodeStateBehavior {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        // While in final flood, the node no longer processes incoming packets.
        // It's focused on sending its final result.
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // The action is to repeatedly flood the final message.
        node.floodMessage(context, node, node.getKnowledge());

        long endOfFloodTime = context.getTime() + node.getFinalFloodCounter();

        context.getSimulator().scheduleEvent(Event.create("FinishedFinalFloodEvent", endOfFloodTime, SimEventPriority.BelowNormal, (ctx) -> {
            if (node.getCurrentState().getStateAsEnum() == ChaosNodeState.FinalFlood) {
                node.setState(new SleepingState(), context);
            }
        }));
    }

    @Override
    public ChaosNodeState getStateAsEnum() {
        return ChaosNodeState.FinalFlood; // Visually, it's still flooding.
    }
}