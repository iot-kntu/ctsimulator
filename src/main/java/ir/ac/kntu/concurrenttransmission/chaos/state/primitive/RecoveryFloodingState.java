package ir.ac.kntu.concurrenttransmission.chaos.state.primitive;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

/**
 * A temporary flooding state used for timeout recovery.
 * It floods the latest knowledge once and then returns to the previous state.
 */
public class RecoveryFloodingState implements NodeState {

    private final NodeState previousState;

    public RecoveryFloodingState(NodeState previousState) {
        System.out.println("RecoveryFloodingState :)");
        this.previousState = previousState;
    }

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        // Ignore packets while recovering.
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        System.out.println("RecoveryFloodingState  flood :)");
        node.floodMessage(context, 0, node, node.getKnowledge());
        long endOfFloodTime = context.getTime()
                + context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedRecoveryFlood", endOfFloodTime, SimEventPriority.High, (ctx) -> {
                    if (ctx.getApplication() instanceof ChaosApplication chaosApp && !chaosApp.isRoundOpen()) {
                        System.out.println(chaosApp.isRoundOpen());
                        return;
                    }
                    if (node.getCurrentState() instanceof RecoveryFloodingState) {
                        NodeState next = previousState != null ? previousState : new ListeningState();
                        node.setState(next, ctx, true);
                    }
                }));
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {
        // No periodic action needed.
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public String toString() {
        return "rT";
    }
}
