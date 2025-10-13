package ir.ac.kntu.distributedsystems.a2.vote;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.FlagField;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * An implementation of ChaosNodeListener for network-wide voting.
 */
public class Vote implements ChaosNodeListener {
    private static final Logger logger = Logger.getLogger(Vote.class.getSimpleName());

    // A queue of initial votes for the node for each round.
    private final Queue<VoteValue> votes;
    private final Queue<Integer> proposals;

    private ChaosMessage roundMessage;

    private final int nodeId;

    public Vote(Queue<VoteValue> votes, Queue<Integer> proposals, int nodeId) {
        this.votes = votes;
        this.proposals = proposals;
        this.nodeId = nodeId;
    }

    @Override
    public CtMessage<ChaosMessage> initiateMessage(ContextView context, CtNode self, CtNode initiator) {
        Object proposal;
        VoteFlag initialFlag;

        if (self.equals(initiator)) {
            // --- I AM THE INITIATOR ---
            // I create the proposal and vote YES on my own proposal.
            proposal = proposals.poll();
            if (proposal == null) proposal = "DEFAULT_PROPOSAL_" + self.getId(); // Fallback
            initialFlag = new VoteFlag(VoteValue.YES);
            logger.info(String.format("Node[%d] (Initiator) proposes: %s", self.getId(), proposal));
        } else {
            // --- I AM A PARTICIPANT ---
            // I don't have a proposal yet, and my vote is undecided.
            proposal = null; // The proposal will be learned from the network.
            initialFlag = VoteFlag.UNDECIDED;
        }

        FlagField initialFlags = FlagField.initial(self.getId(), initialFlag);
        ChaosMessage chaosContent = new ChaosMessage(initialFlags, proposal);
        return new CtMessage<>(initiator, chaosContent);
    }


    @Override
    public CtMessage<?> getRoundMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return roundMessage != null ? new CtMessage<>(context.getNetGraph().getNodeById(nodeId), roundMessage) : CtMessage.NULL_MESSAGE;
    }


    @Override
    public CtMessage<ChaosMessage> merge(ContextView context, FloodPacket<?> receivedPacket) {
        StatefulNode receiver = (StatefulNode) receivedPacket.receiver();

        CtMessage<ChaosMessage> currentKnowledgeMsg = receiver.getKnowledge();
        CtMessage<ChaosMessage> receivedKnowledgeMsg = (CtMessage<ChaosMessage>) receivedPacket.ctMessage();

        ChaosMessage currentContent = currentKnowledgeMsg.content();
        ChaosMessage receivedContent = receivedKnowledgeMsg.content();


        Object currentProposal = currentContent.payload();
        Object receivedProposal = receivedContent.payload();
        Object authoritativeProposal = (receivedProposal != null) ? receivedProposal : currentProposal;

        FlagField mergedFlags = currentContent.flags().merge(receivedContent.flags());
        VoteFlag myCurrentFlag = (VoteFlag) mergedFlags.getFlag(receiver.getId());

        if (myCurrentFlag == null || myCurrentFlag.value() == VoteValue.UNDECIDED) {
            // This is the first time I'm seeing the proposal, so I cast my vote.
            VoteValue myVote = votes.poll();
            if (myVote == null) myVote = VoteValue.NO; // Default to NO if no vote is specified

            // Add my vote to the merged flags.
            mergedFlags = mergedFlags.merge(FlagField.initial(receiver.getId(), new VoteFlag(myVote)));
            logger.info(String.format("Node[%d] voted %s on proposal '%s'", receiver.getId(), myVote, authoritativeProposal));
        }


        ChaosMessage mergedContent = new ChaosMessage(mergedFlags, authoritativeProposal);
        return new CtMessage<>(receivedKnowledgeMsg.initiator(), mergedContent);
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return null;
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket, boolean areSimilar) {
        return true;
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.warning("Packets lost at node " + packets.get(0).receiver().getId() + " due to capture failure.");
    }
}