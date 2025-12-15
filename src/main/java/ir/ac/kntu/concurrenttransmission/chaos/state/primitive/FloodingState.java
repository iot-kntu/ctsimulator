package ir.ac.kntu.concurrenttransmission.chaos.state.primitive;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

/**
 * Behavior of a node when it decides to flood.
 * It sends its knowledge once and then transitions back to Listening.
 */
public class FloodingState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        // A node in a flooding state might ignore incoming packets for simplicity in
        // this model,
        // or it could merge them and decide to re-flood if necessary.
        // For now, we transition back to listening after the flood triggered by
        // onEnter.
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // The primary action of this state is to flood the current knowledge.
        node.floodMessage(context, 0, node, node.getKnowledge());
        long endOfFloodTime = context.getTime()
                + context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedFloodEvent", endOfFloodTime, SimEventPriority.BelowNormal, (ctx) -> {
                    if (ctx.getApplication() instanceof ChaosApplication chaosApp && !chaosApp.isRoundOpen()) {
                        return;
                    }
                    if (node.getCurrentState() instanceof FloodingState) {
                        node.setState(new ListeningState(), ctx, true);
                    }
                }));
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {

    }

    @Override
    public String toString() {
        return "T";
    }
}
