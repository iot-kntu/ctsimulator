package ir.ac.kntu.distributedsystems.a2.threepc;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.Flag;
import ir.ac.kntu.concurrenttransmission.chaos.FlagField;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.aggregation.ParticipationFlag;
import ir.ac.kntu.distributedsystems.a2.vote.VoteFlag;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

public class ThreePhaseCommit implements ChaosNodeListener {
    private static final Logger logger = Logger.getLogger(ThreePhaseCommit.class.getSimpleName());

    private final Queue<Object> proposals; // For the coordinator
    private final Queue<VoteValue> votes; // For participants

    public ThreePhaseCommit(Queue<Object> proposals, Queue<VoteValue> votes) {
        this.proposals = proposals;
        this.votes = votes;
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket,
                                     boolean areSimilar) {
        return true;
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.warning("Packets lost at node " + packets.get(0).receiver().getId());
    }

    @Override
    public CtMessage<ChaosMessage> initiateMessage(ContextView context, CtNode self, CtNode initiator) {
        ThreePhaseCommitPayload payload;
        FlagField initialFlags;

        if (self.equals(initiator)) {
            // I AM THE COORDINATOR
            Object proposal = proposals.poll();
            if (proposal == null)
                proposal = "DEFAULT_3PC_PROPOSAL";
            payload = new ThreePhaseCommitPayload(ThreePhaseCommitPhase.VOTING, proposal,
                    ThreePhaseCommitDecision.IN_PROGRESS);
            initialFlags = FlagField.initial(self.getId(), new VoteFlag(VoteValue.YES));
        } else {
            // I AM A PARTICIPANT
            // I start with no knowledge and an undecided vote.
            payload = new ThreePhaseCommitPayload(ThreePhaseCommitPhase.VOTING, null, ThreePhaseCommitDecision.IN_PROGRESS);
            initialFlags = FlagField.initial(self.getId(), VoteFlag.UNDECIDED);
        }

        ChaosMessage chaosContent = new ChaosMessage(initialFlags, payload);
        return new CtMessage<>(initiator, chaosContent);
    }

    @Override
    public CtMessage<?> getRoundMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return null;
    }

    @Override
    public CtMessage<ChaosMessage> merge(ContextView context, FloodPacket<?> receivedPacket) {
        logger.info(String.format("Node[%d]: Received packet from Node[%d]", receivedPacket.receiver().getId(),
                receivedPacket.sender().getId()));
        // This method now ONLY merges data. State transitions are handled by the states
        // themselves.
        StatefulNode receiver = (StatefulNode) receivedPacket.receiver();
        CtMessage<ChaosMessage> currentKnowledge = receiver.getKnowledge();
        CtMessage<ChaosMessage> receivedKnowledge = (CtMessage<ChaosMessage>) receivedPacket.ctMessage();

        ThreePhaseCommitPayload currentPayload = (ThreePhaseCommitPayload) currentKnowledge.content().payload();
        ThreePhaseCommitPayload receivedPayload = (ThreePhaseCommitPayload) receivedKnowledge.content().payload();

        if (currentPayload.phase() == ThreePhaseCommitPhase.FINALIZING
                || receivedPayload.phase() == ThreePhaseCommitPhase.FINALIZING) {
            return handleFinalizingPhase(receiver, currentKnowledge, receivedKnowledge);
        } else if (currentPayload.phase() == ThreePhaseCommitPhase.PRE_COMMIT
                || receivedPayload.phase() == ThreePhaseCommitPhase.PRE_COMMIT) {
            return handlePreCommitPhase(context, receiver, currentKnowledge, receivedKnowledge);
        } else {
            // Otherwise, both are in the VOTING phase.
            return handleVotingPhase(context, receiver, currentKnowledge, receivedKnowledge);
        }
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return null;
    }

    private CtMessage<ChaosMessage> handleFinalizingPhase(StatefulNode receiver,
                                                          CtMessage<ChaosMessage> currentKnowledge,
                                                          CtMessage<ChaosMessage> receivedKnowledge) {

        ThreePhaseCommitPayload currentPayload = (ThreePhaseCommitPayload) currentKnowledge.content().payload();
        ThreePhaseCommitPayload receivedPayload = (ThreePhaseCommitPayload) receivedKnowledge.content().payload();

        FlagField ackFlags = receivedKnowledge.content().flags();

        if (currentPayload.phase() == ThreePhaseCommitPhase.FINALIZING
                && receivedPayload.phase() == ThreePhaseCommitPhase.VOTING) {
            ackFlags = currentKnowledge.content().flags();
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, currentPayload));
        } else if (currentPayload.phase() == ThreePhaseCommitPhase.VOTING
                && receivedPayload.phase() == ThreePhaseCommitPhase.FINALIZING) {
            ackFlags = ackFlags.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, receivedPayload));
        } else if (currentPayload.phase() == ThreePhaseCommitPhase.FINALIZING
                && receivedPayload.phase() == ThreePhaseCommitPhase.PRE_COMMIT) {
            ackFlags = currentKnowledge.content().flags();
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, currentPayload));
        } else if (currentPayload.phase() == ThreePhaseCommitPhase.PRE_COMMIT
                && receivedPayload.phase() == ThreePhaseCommitPhase.FINALIZING) {
            ackFlags = ackFlags.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, receivedPayload));
        } else {
            ackFlags = ackFlags.merge(currentKnowledge.content().flags());
            ackFlags = ackFlags.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, receivedPayload));
        }
    }

    private CtMessage<ChaosMessage> handlePreCommitPhase(ContextView context, StatefulNode receiver,
                                                         CtMessage<ChaosMessage> currentKnowledge,
                                                         CtMessage<ChaosMessage> receivedKnowledge) {

        ThreePhaseCommitPayload currentPayload = (ThreePhaseCommitPayload) currentKnowledge.content().payload();
        ThreePhaseCommitPayload receivedPayload = (ThreePhaseCommitPayload) receivedKnowledge.content().payload();

        FlagField ackFlags = receivedKnowledge.content().flags();

        if (currentPayload.phase() == ThreePhaseCommitPhase.PRE_COMMIT
                && receivedPayload.phase() == ThreePhaseCommitPhase.VOTING) {
            ackFlags = currentKnowledge.content().flags();
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, currentPayload));
        } else if (currentPayload.phase() == ThreePhaseCommitPhase.VOTING
                && receivedPayload.phase() == ThreePhaseCommitPhase.PRE_COMMIT) {
            ackFlags = ackFlags.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, receivedPayload));
        } else {
            ackFlags = ackFlags.merge(currentKnowledge.content().flags());
            ackFlags = ackFlags.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(ackFlags, receivedPayload));
        }
    }

    private CtMessage<ChaosMessage> handleVotingPhase(ContextView context, StatefulNode receiver,
                                                      CtMessage<ChaosMessage> currentKnowledge,
                                                      CtMessage<ChaosMessage> receivedKnowledge) {

        ThreePhaseCommitPayload currentPayload = (ThreePhaseCommitPayload) currentKnowledge.content().payload();
        ThreePhaseCommitPayload receivedPayload = (ThreePhaseCommitPayload) receivedKnowledge.content().payload();

        Object proposal = (receivedPayload.proposal() != null) ? receivedPayload.proposal() : currentPayload.proposal();
        FlagField mergedFlags = currentKnowledge.content().flags().merge(receivedKnowledge.content().flags());

        // Cast vote if this node hasn't voted yet.
        Flag currentFlag = mergedFlags.getFlag(receiver.getId());
        // This check prevents ClassCastException by ensuring we only cast VoteFlags in
        // the voting phase.
        if (currentFlag instanceof VoteFlag && ((VoteFlag) currentFlag).value() == VoteValue.UNDECIDED) {
            if (!receiver.equals(receivedKnowledge.initiator())) {
                VoteValue myVote = votes.poll();
                if (myVote == null)
                    myVote = VoteValue.NO;
                mergedFlags = mergedFlags.merge(FlagField.initial(receiver.getId(), new VoteFlag(myVote)));
                logger.info(String.format("Node[%d] voted %s", receiver.getId(), myVote));
            }
        }

        // --- COORDINATOR'S RESPONSIBILITY ---
        if (receiver.equals(receivedKnowledge.initiator())) {
            int totalNodes = context.getNetGraph().getNodeCount();

            // Check if voting is complete.
            if (mergedFlags.getParticipationCount() == totalNodes) {
                // All nodes have voted. Time to make a decision.
                boolean allVotedYes = mergedFlags.flags().values().stream()
                        .allMatch(flag -> ((VoteFlag) flag).value() == VoteValue.YES);

                ThreePhaseCommitDecision decision = allVotedYes ? ThreePhaseCommitDecision.PRE_COMMIT
                        : ThreePhaseCommitDecision.ABORT;

                // Transition to PRE_COMMIT phase with new, reset flags.
                FlagField preCommitFlags = FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED);
                ThreePhaseCommitPayload preCommitPayload = new ThreePhaseCommitPayload(ThreePhaseCommitPhase.PRE_COMMIT, proposal,
                        decision);

                logger.info(String.format("Coordinator Node[%d] sent PRE_COMMIT. Decision: %s. Entering PRE_COMMIT phase.",
                        receiver.getId(), decision));

                return new CtMessage<>(receivedKnowledge.initiator(),
                        new ChaosMessage(preCommitFlags, preCommitPayload));
            }
        }

        // If I am a participant OR the coordinator but voting is not over,
        // just create a new message with the updated votes in the VOTING phase.
        ThreePhaseCommitPayload updatedVotingPayload = new ThreePhaseCommitPayload(ThreePhaseCommitPhase.VOTING, proposal,
                ThreePhaseCommitDecision.IN_PROGRESS);
        ChaosMessage mergedContent = new ChaosMessage(mergedFlags, updatedVotingPayload);
        return new CtMessage<>(receivedKnowledge.initiator(), mergedContent);
    }
}