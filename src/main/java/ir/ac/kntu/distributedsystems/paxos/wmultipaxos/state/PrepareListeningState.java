package ir.ac.kntu.distributedsystems.paxos.wmultipaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.paxos.wmultipaxos.WirelessMultiPaxosPayload;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosPhase;

import java.util.Objects;

/**
 * Listens for Prepare packets, merges them, and floods if knowledge advanced.
 */
public class PrepareListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> before = node.getKnowledge();
        CtMessage<ChaosMessage> merged = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(merged);

        WirelessMultiPaxosPayload beforePayload = payloadOrEmpty(before);
        WirelessMultiPaxosPayload afterPayload = payloadOrEmpty(merged);

        boolean payloadChanged = !Objects.equals(beforePayload, afterPayload);
        boolean shouldFlood = payloadChanged || node.shouldFlood(before, merged);

        if (afterPayload.phase() == WirelessPaxosPhase.ACCEPT) {
            if (shouldFlood) {
                node.setState(new AcceptFloodingState(), context);
            } else {
                node.setState(new AcceptListeningState(), context);
            }
            return;
        }

        if (shouldFlood) {
            node.setState(new PrepareFloodingState(), context);
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
    public String toString() {
        return "pR";
    }

    private WirelessMultiPaxosPayload payloadOrEmpty(CtMessage<ChaosMessage> message) {
        Object payload = message != null && message.content() != null ? message.content().payload() : null;
        if (payload instanceof WirelessMultiPaxosPayload mpPayload) {
            return mpPayload;
        }
        return WirelessMultiPaxosPayload.empty(0);
    }
}
