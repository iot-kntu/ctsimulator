package ir.ac.kntu.distributedsystems.paxos.wmultipaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.FinalFloodingState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.paxos.wmultipaxos.WirelessMultiPaxos;
import ir.ac.kntu.distributedsystems.paxos.wmultipaxos.WirelessMultiPaxosPayload;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosPhase;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

import java.util.Objects;

/**
 * Listens to Accept packets and floods when knowledge advances.
 */
public class AcceptListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> before = node.getKnowledge();
        CtMessage<ChaosMessage> merged = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(merged);

        WirelessMultiPaxosPayload beforePayload = payloadOrEmpty(before);
        WirelessMultiPaxosPayload afterPayload = payloadOrEmpty(merged);

        recordPhase(context, node, WirelessPaxosPhase.ACCEPT);

        if (listener instanceof WirelessMultiPaxos multiPaxos && multiPaxos.shouldSleep(afterPayload)) {
            node.setState(new FinalFloodingState(), context);
            return;
        }

        boolean payloadChanged = !Objects.equals(beforePayload, afterPayload);
        boolean shouldFlood = payloadChanged || node.shouldFlood(before, merged);

        if (shouldFlood) {
            node.setState(new AcceptFloodingState(), context);
        }
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // Passive listening.
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {
        // No periodic action needed.
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
        return WirelessMultiPaxosPayload.empty(0);
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
}
