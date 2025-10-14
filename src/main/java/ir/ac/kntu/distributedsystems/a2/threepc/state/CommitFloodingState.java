package ir.ac.kntu.distributedsystems.a2.threepc.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

public class CommitFloodingState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {

    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        node.floodMessage(context, node, node.getKnowledge());
        long endOfFloodTime = context.getTime()
                + context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedFloodEvent", endOfFloodTime, SimEventPriority.BelowNormal, (ctx) -> {
                    if (node.getCurrentState() instanceof CommitFloodingState) {
                        node.setState(new CommitListeningState(), context);
                    }
                }));
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {

    }

    @Override
    public String toString() {
        return "cT";
    }
}