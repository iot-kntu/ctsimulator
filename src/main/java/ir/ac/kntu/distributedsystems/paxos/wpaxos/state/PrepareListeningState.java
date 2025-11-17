package ir.ac.kntu.distributedsystems.paxos.wpaxos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosPayload;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosPhase;

/**
 * Behavior of a node when it is in the Listening state.
 * In this state, the node processes incoming packets and decides whether to
 * transition
 * to a flooding state.
 */
public class PrepareListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();

        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(mergedMessage);

        WirelessPaxosPayload payload = (WirelessPaxosPayload) mergedMessage.content().payload();

        if (payload.phase() == WirelessPaxosPhase.ACCEPT) {
            if (node.shouldFlood(currentKnowledge, (CtMessage<ChaosMessage>) capturedPacket.ctMessage())) {
                node.setState(new AcceptFloodingState(), context);
            } else {
                node.setState(new AcceptListeningState(), context);
            }
            return;
        }

        if (node.shouldFlood(currentKnowledge, (CtMessage<ChaosMessage>) capturedPacket.ctMessage())) {
            node.setState(new PrepareFloodingState(), context);
        }
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // Nothing to do when entering listen state.
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {

    }

    @Override
    public String toString() {
        return "pR";
    }
}
