package ir.ac.kntu.distributedsystems.bf.threepc;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommitDecision;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BlueFlood implementation of three phase commit.
 * Each node proposes once in its own round, exchanges full knowledge, and progresses
 * through VOTING -> PRE_COMMIT -> COMMIT/ABORT for its own proposal.
 */
public class BlueFloodThreePhaseCommit implements BlueFloodNodeListener {

    private static final Logger logger = Logger.getLogger(BlueFloodThreePhaseCommit.class.getSimpleName());

    private final Queue<Object> proposals;
    private final Queue<VoteValue> votePlan;

    private final Map<Integer, Object> knownProposals = new HashMap<>();
    private final Map<Integer, Map<Integer, VoteValue>> votesByProposal = new HashMap<>();
    private final Map<Integer, ThreePhaseCommitDecision> decisions = new HashMap<>();
    private final Map<Integer, Set<Integer>> preCommitAcks = new HashMap<>();
    private final Set<Integer> deliveredMessages = new HashSet<>();

    private int networkSize = 0;
    private boolean hasProposed = false;

    public BlueFloodThreePhaseCommit(Queue<Object> proposals, Queue<VoteValue> votePlan) {
        this.proposals = proposals;
        this.votePlan = votePlan;
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket,
                                     boolean areSimilar) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(selectedPacket);
        ensureInitialized(context);

        CtMessage<?> ctMessage = selectedPacket.ctMessage();
        if (ctMessage.isNull() || !(ctMessage.content() instanceof ThreePcPayload payload)) {
            return false;
        }

        int receiverId = selectedPacket.receiver().getId();

        mergeIncomingProposals(payload);
        mergeIncomingVotes(payload);
        mergeIncomingDecisions(payload);
        ensureVotesForKnownProposals(receiverId);
        maybeAdvanceOwnProposal(receiverId);

        return deliveredMessages.add(ctMessage.messageNo());
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.log(Level.INFO, "Packets lost for node " + packets.get(0).receiver().getId());
    }

    @Override
    public CtMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(initiator);
        ensureInitialized(context);

        int selfId = initiator.getId();
        Object proposalToSend = null;

        if (!hasProposed) {
            proposalToSend = proposals.poll();
            if (proposalToSend == null) {
                proposalToSend = "proposal-" + selfId;
            }
            registerProposal(selfId, proposalToSend);
            hasProposed = true;
        }

        ensureVotesForKnownProposals(selfId);
        maybeAdvanceOwnProposal(selfId);

        Map<Integer, Map<Integer, VoteValue>> outgoingVotes = deepCopyVotes();
        Map<Integer, ThreePhaseCommitDecision> outgoingDecisions = new HashMap<>(decisions);
        Map<Integer, Object> outgoingProposals = new HashMap<>(knownProposals);

        ThreePcPayload payload = new ThreePcPayload(selfId, outgoingProposals, outgoingVotes, outgoingDecisions);
        return new CtMessage<>(initiator, payload);
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return receivedMessage;
    }

    private void ensureInitialized(ContextView context) {
        if (networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
        }
    }

    private void registerProposal(int proposerId, Object proposal) {
        if (proposal == null || knownProposals.containsKey(proposerId)) {
            return;
        }

        knownProposals.put(proposerId, proposal);
        decisions.putIfAbsent(proposerId, ThreePhaseCommitDecision.IN_PROGRESS);
    }

    private void mergeIncomingProposals(ThreePcPayload payload) {
        if (payload.proposals() == null || payload.proposals().isEmpty()) {
            return;
        }
        payload.proposals().forEach(this::registerProposal);
    }

    private void mergeIncomingVotes(ThreePcPayload payload) {
        if (payload.votes() == null || payload.votes().isEmpty()) {
            return;
        }
        payload.votes().forEach((proposalOwner, voteMap) -> {
            if (voteMap == null) {
                return;
            }
            voteMap.forEach((voterId, vote) -> {
                if (vote == null) {
                    return;
                }
                votesByProposal.computeIfAbsent(proposalOwner, key -> new HashMap<>())
                        .put(voterId, vote);
                decisions.putIfAbsent(proposalOwner, ThreePhaseCommitDecision.IN_PROGRESS);
            });
        });
    }

    private void mergeIncomingDecisions(ThreePcPayload payload) {
        if (payload.decisions() == null) {
            return;
        }

        payload.decisions().forEach((proposalOwner, incomingDecision) -> {
            ThreePhaseCommitDecision current = decisions.get(proposalOwner);
            decisions.put(proposalOwner, mergeDecision(current, incomingDecision));
            if (incomingDecision == ThreePhaseCommitDecision.PRE_COMMIT
                    || incomingDecision == ThreePhaseCommitDecision.COMMIT) {
                preCommitAcks.computeIfAbsent(proposalOwner, key -> new HashSet<>())
                        .add(payload.authorId());
            }
        });
    }

    private ThreePhaseCommitDecision mergeDecision(ThreePhaseCommitDecision current,
                                                   ThreePhaseCommitDecision incoming) {
        if (incoming == null) {
            return current;
        }
        if (current == null) {
            return incoming;
        }
        if (current == ThreePhaseCommitDecision.ABORT || incoming == ThreePhaseCommitDecision.ABORT) {
            return ThreePhaseCommitDecision.ABORT;
        }
        if (current == ThreePhaseCommitDecision.COMMIT || incoming == ThreePhaseCommitDecision.COMMIT) {
            return ThreePhaseCommitDecision.COMMIT;
        }
        if (current == ThreePhaseCommitDecision.PRE_COMMIT || incoming == ThreePhaseCommitDecision.PRE_COMMIT) {
            return ThreePhaseCommitDecision.PRE_COMMIT;
        }
        return ThreePhaseCommitDecision.IN_PROGRESS;
    }

    private void ensureVotesForKnownProposals(int selfId) {
        knownProposals.keySet().forEach(proposalOwner -> {
            Map<Integer, VoteValue> perProposal = votesByProposal.computeIfAbsent(proposalOwner, key -> new HashMap<>());
            if (!perProposal.containsKey(selfId)) {
                VoteValue vote = nextVote();
                perProposal.put(selfId, vote);
            }
        });
    }

    private VoteValue nextVote() {
        VoteValue vote = votePlan.poll();
        return vote != null ? vote : VoteValue.YES;
    }

    private void maybeAdvanceOwnProposal(int selfId) {
        if (!knownProposals.containsKey(selfId)) {
            return;
        }

        ThreePhaseCommitDecision currentDecision = decisions.get(selfId);
        if (currentDecision == ThreePhaseCommitDecision.ABORT
                || currentDecision == ThreePhaseCommitDecision.COMMIT) {
            return;
        }

        Map<Integer, VoteValue> votes = votesByProposal.get(selfId);
        if (votes == null || votes.size() < networkSize) {
            return;
        }

        boolean anyNo = votes.values().stream().anyMatch(vote -> vote == VoteValue.NO);
        boolean anyUndecided = votes.values().stream().anyMatch(vote -> vote == VoteValue.UNDECIDED);

        if (anyNo) {
            decisions.put(selfId, ThreePhaseCommitDecision.ABORT);
            logger.log(Level.INFO,
                    "Node[" + selfId + "] aborted its proposal after receiving a NO vote");
            return;
        }

        if (anyUndecided) {
            return;
        }

        if (currentDecision == null || currentDecision == ThreePhaseCommitDecision.IN_PROGRESS) {
            decisions.put(selfId, ThreePhaseCommitDecision.PRE_COMMIT);
            preCommitAcks.computeIfAbsent(selfId, key -> new HashSet<>()).add(selfId);
            logger.log(Level.INFO, "Node[" + selfId + "] reached PRE_COMMIT");
        } else if (currentDecision == ThreePhaseCommitDecision.PRE_COMMIT) {
            Set<Integer> acks = preCommitAcks.getOrDefault(selfId, Set.of());
            if (acks.size() < networkSize) {
                return;
            }
            decisions.put(selfId, ThreePhaseCommitDecision.COMMIT);
            logger.log(Level.INFO, "Node[" + selfId + "] finalized COMMIT");
        }
    }

    private Map<Integer, Map<Integer, VoteValue>> deepCopyVotes() {
        Map<Integer, Map<Integer, VoteValue>> copy = new HashMap<>();
        votesByProposal.forEach((proposalOwner, votes) -> copy.put(proposalOwner, new HashMap<>(votes)));
        return copy;
    }
}
