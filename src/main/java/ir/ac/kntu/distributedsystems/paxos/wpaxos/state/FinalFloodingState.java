package ir.ac.kntu.distributedsystems.paxos.wpaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.SleepingState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

public class FinalFloodingState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {

    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        node.floodMessage(context, 0, node, node.getKnowledge(), true);

        long endOfFloodTime = context.getTime() + node.getFinalFloodCounter();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedFinalFloodEvent", endOfFloodTime, SimEventPriority.High, (ctx) -> {
                    if (ctx.getApplication() instanceof ChaosApplication chaosApp && !chaosApp.isRoundOpen()) {
                        return;
                    }
                    if (node.getCurrentState() instanceof FinalFloodingState) {
                        node.setState(new SleepingState(), ctx, true);
                    }
                }));
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {

    }

    @Override
    public String toString() {
        return "F";
    }
}
