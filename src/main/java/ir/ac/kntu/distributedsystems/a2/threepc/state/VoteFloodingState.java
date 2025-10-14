package ir.ac.kntu.distributedsystems.a2.threepc.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;
import ir.ac.kntu.distributedsystems.a2.vote.VoteFlag;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

public class VoteFloodingState implements NodeState {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {

    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        CtMessage<ChaosMessage> message = node.getKnowledge();
        int totalNodes = context.getNetGraph().getNodeCount();
        boolean votingWouldBeComplete = message.content().flags().getParticipationCount() == totalNodes;
        boolean allVotesKnown = message.content().flags().flags().values().stream()
                .allMatch(flag -> (flag instanceof VoteFlag) && ((VoteFlag) flag).value() != VoteValue.UNDECIDED);

        node.floodMessage(context, node, message);
        long endOfFloodTime = context.getTime()
                + context.getApplication().getTransmissionPolicy().getFloodRepeatCount();

        context.getSimulator().scheduleEvent(
                Event.create("FinishedFloodEvent", endOfFloodTime, SimEventPriority.BelowNormal, (ctx) -> {
                    if (node.getCurrentState() instanceof VoteFloodingState) {
                        if (votingWouldBeComplete && allVotesKnown) {
                            node.setState(new PreCommitWaitingState(), context);
                        } else {
                            node.setState(new VoteListeningState(), context);
                        }
                    }
                }));
    }

    @Override
    public void onSlotStart(StatefulNode node, ContextView context) {

    }

    @Override
    public String toString() {
        return "vT";
    }
}