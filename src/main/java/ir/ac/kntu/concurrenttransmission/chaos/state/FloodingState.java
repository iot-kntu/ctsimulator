package ir.ac.kntu.concurrenttransmission.chaos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.NodeStateBehavior;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FinishedFloodEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

/**
 * Behavior of a node when it decides to flood.
 * It sends its knowledge once and then transitions back to Listening.
 */
public class FloodingState implements NodeStateBehavior {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        // A node in a flooding state might ignore incoming packets for simplicity in this model,
        // or it could merge them and decide to re-flood if necessary.
        // For now, we transition back to listening after the flood triggered by onEnter.
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // The primary action of this state is to flood the current knowledge.
        node.floodMessage(context, node, node.getKnowledge());

        long endOfFloodTime = context.getTime() + context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        context.getSimulator().scheduleEvent(Event.create("FinishedFloodEvent", endOfFloodTime, SimEventPriority.BelowNormal, (ctx) -> {
            if (node.getCurrentState().getStateAsEnum() == ChaosNodeState.Flood) {
                node.setState(new ListeningState(), context);
            }
        }));

//        FinishedFloodEvent endEvent = new FinishedFloodEvent(endOfFloodTime, node);
//        context.getSimulator().scheduleEvent(endEvent);
    }

    @Override
    public ChaosNodeState getStateAsEnum() {
        return ChaosNodeState.Flood;
    }
}