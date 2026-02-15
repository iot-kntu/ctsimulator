package ir.ac.kntu.distributedsystems.a2.wmultipaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.wmultipaxos.WirelessMultiPaxos;
import ir.ac.kntu.distributedsystems.a2.wmultipaxos.WirelessMultiPaxosPayload;
import ir.ac.kntu.distributedsystems.a2.wmultipaxos.WirelessMultiPaxosPhase;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;
import ir.ac.kntu.distributedsystems.a2.wmultipaxos.state.RecoveryFloodingState;

import java.util.Objects;

public class AcceptListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();
        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(mergedMessage);

        WirelessMultiPaxosPayload before = payloadOrEmpty(currentKnowledge);
        WirelessMultiPaxosPayload after = payloadOrEmpty(mergedMessage);

        recordPhase(context, node, WirelessMultiPaxosPhase.ACCEPT);

        if (listener instanceof WirelessMultiPaxos multiPaxos) {
            if (multiPaxos.shouldEndByIdle(context)) {
                if (multiPaxos.consumeRecovery(context)) {
                    node.setState(new RecoveryFloodingState(new AcceptListeningState()), context);
                } else {
                    node.setState(new FinalFloodingState(), context);
                }
                return;
            }
            if (mergedMessage.content() != null
                    && multiPaxos.shouldFinalize(after, mergedMessage.content().flags())) {
                node.setState(new FinalFloodingState(), context);
                return;
            }
        }

        boolean payloadChanged = !Objects.equals(before, after);
        boolean shouldFlood = payloadChanged || node.shouldFlood(currentKnowledge, mergedMessage);

        if (shouldFlood) {
            node.setState(new AcceptFloodingState(), context);
        }
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        if (listener instanceof WirelessMultiPaxos multiPaxos && multiPaxos.shouldEndByIdle(context)) {
            if (multiPaxos.consumeRecovery(context)) {
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

    private WirelessMultiPaxosPayload payloadOrEmpty(CtMessage<ChaosMessage> message) {
        Object payload = message != null && message.content() != null ? message.content().payload() : null;
        if (payload instanceof WirelessMultiPaxosPayload mpPayload) {
            return mpPayload;
        }
        return WirelessMultiPaxosPayload.prepare(-1, 0, 0, -1, null);
    }

    private void recordPhase(ContextView context, StatefulNode node, WirelessMultiPaxosPhase phase) {
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
}
