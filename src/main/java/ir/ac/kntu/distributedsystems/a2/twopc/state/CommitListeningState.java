package ir.ac.kntu.distributedsystems.a2.twopc.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

public class CommitListeningState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();

        CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
        node.setKnowledge(mergedMessage);

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
    public String toString() {
        return "cR";
    }
}
