package ir.ac.kntu.concurrenttransmission.chaos.state.primitive;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

/**
 * Behavior of a node when it has reached completion and enters the final flood
 * phase.
 */
public class FinalFloodingState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        // While in final flood, the node no longer processes incoming packets.
        // It's focused on sending its final result.
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // The action is to repeatedly flood the final message.
        recordDecisionEnd(context, node);
        node.floodMessage(context, 0, node, node.getKnowledge(), true);

        long endOfFloodTime = context.getTime() + node.getFinalFloodCounter();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedFinalFloodEvent", endOfFloodTime, SimEventPriority.BelowNormal, (ctx) -> {
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
