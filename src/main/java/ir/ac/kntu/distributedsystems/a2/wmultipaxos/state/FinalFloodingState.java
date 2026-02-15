package ir.ac.kntu.distributedsystems.a2.wmultipaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.SleepingState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

public class FinalFloodingState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        recordDecisionEnd(context, node);
        node.floodMessage(context, 0, node, node.getKnowledge(), true);

        long endOfFloodTime = context.getTime() + node.getFinalFloodCounter();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedFinalFlood", endOfFloodTime, SimEventPriority.High, (ctx) -> {
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

    private void recordDecisionEnd(ContextView context, StatefulNode node) {
        if (context == null || node == null) {
            return;
        }
        if (context.getApplication() instanceof MetricsEmitter emitter) {
            MetricsCollector metrics = emitter.getMetricsCollector();
            if (metrics != null && context.getApplication().getNetworkTime() != null) {
                int round = context.getApplication().getNetworkTime().round();
                metrics.recordDecisionEnd(round, node.getId(), context.getTime(), true);
            }
        }
    }
}
