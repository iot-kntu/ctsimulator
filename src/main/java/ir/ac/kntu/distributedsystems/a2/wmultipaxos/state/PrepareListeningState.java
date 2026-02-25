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

import java.util.Objects;

public class PrepareListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();
        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(mergedMessage);

        WirelessMultiPaxosPayload before = payloadOrEmpty(currentKnowledge);
        WirelessMultiPaxosPayload after = payloadOrEmpty(mergedMessage);

        if (listener instanceof WirelessMultiPaxos multiPaxos) {
            if (multiPaxos.shouldEndByIdle(context)) {
                if (multiPaxos.consumeRecovery(context)) {
                    node.setState(new RecoveryFloodingState(new PrepareListeningState()), context);
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

        if (after.phase() == WirelessMultiPaxosPhase.ACCEPT) {
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
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        if (listener instanceof WirelessMultiPaxos multiPaxos && multiPaxos.shouldEndByIdle(context)) {
            if (multiPaxos.consumeRecovery(context)) {
                node.setState(new RecoveryFloodingState(new PrepareListeningState()), context);
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
        return "pR";
    }

    private WirelessMultiPaxosPayload payloadOrEmpty(CtMessage<ChaosMessage> message) {
        Object payload = message != null && message.content() != null ? message.content().payload() : null;
        if (payload instanceof WirelessMultiPaxosPayload mpPayload) {
            return mpPayload;
        }
        return WirelessMultiPaxosPayload.prepare(-1, 0, 0, -1, null);
    }
}
