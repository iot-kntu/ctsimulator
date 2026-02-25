package ir.ac.kntu.distributedsystems.paxos.wpaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.FinalFloodingState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxos;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosPhase;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

/**
 * Behavior of a node when it is in the Listening state.
 * In this state, the node processes incoming packets and decides whether to
 * transition
 * to a flooding state.
 */
public class AcceptListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();

        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(mergedMessage);

        recordPhase(context, node, WirelessPaxosPhase.ACCEPT);

        if (listener instanceof WirelessPaxos paxos) {
            if (paxos.shouldEndByIdle(context)) {
                if (paxos.consumeRecovery(context)) {
                    node.setState(new RecoveryFloodingState(new AcceptListeningState()), context);
                } else {
                    node.setState(new FinalFloodingState(), context);
                }
                return;
            }
        }

        int quorum = Math.max(1, (context.getNetGraph().getNodeCount() / 2) + 1);
        if (mergedMessage.content().flags().getParticipationCount() >= quorum) {
            recordDecisionEnd(context, node);
            node.setState(new FinalFloodingState(), context);
            return;
        }

        if (node.shouldFlood(currentKnowledge, (CtMessage<ChaosMessage>) capturedPacket.ctMessage())) {
            node.setState(new AcceptFloodingState(), context);
        }
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // Nothing to do when entering listen state.
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        if (listener instanceof WirelessPaxos paxos && paxos.shouldEndByIdle(context)) {
            if (paxos.consumeRecovery(context)) {
                node.setState(new RecoveryFloodingState(new AcceptListeningState()), context);
            } else {
                node.setState(new FinalFloodingState(), context);
            }
        }
    }

    @Override
    public boolean isListening() {
        return true;
    }

    @Override
    public String toString() {
        return "aR";
    }

    private void recordPhase(ContextView context, StatefulNode node, WirelessPaxosPhase phase) {
        if (context == null || phase == null) {
            return;
        }
        if (context.getApplication() instanceof MetricsEmitter emitter) {
            MetricsCollector metrics = emitter.getMetricsCollector();
            if (metrics != null && context.getApplication().getNetworkTime() != null) {
                int round = context.getApplication().getNetworkTime().round();
                metrics.recordPhaseTime(round, node.getId(), phase.name(), context.getTime());
            }
        }
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
                metrics.recordPhaseTime(round, node.getId(), "DECIDE", context.getTime());
            }
        }
    }
}
