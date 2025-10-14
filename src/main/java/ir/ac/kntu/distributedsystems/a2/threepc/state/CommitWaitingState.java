package ir.ac.kntu.distributedsystems.a2.threepc.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommitPayload;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommitPhase;
import ir.ac.kntu.error.InvalidPhaseException;

public class CommitWaitingState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {

        CtMessage<ChaosMessage> currentKnowledge = node.getKnowledge();

        CtMessage<ChaosMessage> receivedMessage = (CtMessage<ChaosMessage>) capturedPacket.ctMessage();
        ChaosMessage messageContent = receivedMessage.content();
        ThreePhaseCommitPayload payload = (ThreePhaseCommitPayload) messageContent.payload();
        if (payload.phase().equals(ThreePhaseCommitPhase.FINALIZING)) {
            ChaosNodeListener listener = ((ChaosApplication) context.getApplication()).getChaosNodeListener(node);
            CtMessage<ChaosMessage> mergedMessage = (CtMessage<ChaosMessage>) listener.merge(context, capturedPacket);
            node.setKnowledge(mergedMessage);

            if (node.shouldFlood(currentKnowledge, (CtMessage<ChaosMessage>) capturedPacket.ctMessage())) {
                node.setState(new CommitFloodingState(), context);
            }
        } else if (payload.phase().equals(ThreePhaseCommitPhase.VOTING)) {
            throw new InvalidPhaseException(
                    "Unexpected VOTING phase in CommitWaitingState (node " + node.getId() + ")"
            );
        } else {
            if (node.shouldFlood(currentKnowledge, (CtMessage<ChaosMessage>) capturedPacket.ctMessage())) {
                node.setState(new PreCommitFloodingState(), context);
            }
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
        return "W";
    }
}