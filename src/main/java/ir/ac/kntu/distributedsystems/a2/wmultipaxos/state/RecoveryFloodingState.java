package ir.ac.kntu.distributedsystems.a2.wmultipaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

public class RecoveryFloodingState implements NodeState {

    private final NodeState nextState;

    public RecoveryFloodingState(NodeState nextState) {
        this.nextState = nextState;
    }

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        CtMessage<ChaosMessage> message = node.getKnowledge();
        node.floodMessage(context, 0, node, message);

        long endOfFloodTime = context.getTime()
                + context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedRecoveryFlood", endOfFloodTime, SimEventPriority.High, (ctx) -> {
                    if (ctx.getApplication() instanceof ChaosApplication chaosApp && !chaosApp.isRoundOpen()) {
                        return;
                    }
                    if (node.getCurrentState() instanceof RecoveryFloodingState) {
                        NodeState target = nextState != null ? nextState : new AcceptListeningState();
                        node.setState(target, ctx, true);
                    }
                }));
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {
    }

    @Override
    public String toString() {
        return "rT";
    }
}
