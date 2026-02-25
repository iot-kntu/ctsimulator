package ir.ac.kntu.distributedsystems.a2.threepc.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommitDecision;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommitPayload;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

public class CommitListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();

        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(mergedMessage);

        ThreePhaseCommitPayload payload = (ThreePhaseCommitPayload) mergedMessage.content().payload();
        recordDecisionEnd(context, node, payload.decision());
        recordPhase(context, node);

        int totalNodes = context.getNetGraph().getNodeCount();
        if (mergedMessage.content().flags().getParticipationCount() == totalNodes) {
            node.setState(new FinalFloodingState(), context);
            return;
        }

        if (node.shouldFlood(currentKnowledge, (CtMessage<ChaosMessage>) capturedPacket.ctMessage())) {
            node.setState(new CommitFloodingState(), context);
        }
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {

    }

    @Override
    public boolean isListening() {
        return true;
    }

    @Override
    public String toString() {
        return "cR";
    }

    private void recordPhase(ContextView context, StatefulNode node) {
        if (context == null) {
            return;
        }
        if (context.getApplication() instanceof MetricsEmitter emitter) {
            MetricsCollector metrics = emitter.getMetricsCollector();
            if (metrics != null && context.getApplication().getNetworkTime() != null) {
                int round = context.getApplication().getNetworkTime().round();
                metrics.recordPhaseTime(round, node.getId(), "COMMIT", context.getTime());
            }
        }
    }

    private void recordDecisionEnd(ContextView context, StatefulNode node, ThreePhaseCommitDecision decision) {
        if (context == null || node == null || decision == null
                || decision == ThreePhaseCommitDecision.IN_PROGRESS
                || decision == ThreePhaseCommitDecision.PRE_COMMIT) {
            return;
        }
        if (context.getApplication() instanceof MetricsEmitter emitter) {
            MetricsCollector metrics = emitter.getMetricsCollector();
            if (metrics != null && context.getApplication().getNetworkTime() != null) {
                int round = context.getApplication().getNetworkTime().round();
                boolean committed = decision == ThreePhaseCommitDecision.COMMIT;
                metrics.recordDecisionEnd(round, node.getId(), context.getTime(), committed);
                metrics.recordPhaseTime(round, node.getId(), "DECIDE", context.getTime());
            }
        }
    }
}
