package ir.ac.kntu.distributedsystems.bf.twopc;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.twopc.TwoPhaseCommitDecision;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

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
 * A BlueFlood-based implementation of two phase commit.
 * Each node proposes in its own round and shares its votes for previously seen
 * proposals. Messages are immutable (BlueFlood), but nodes keep local state to
 * track votes/decisions and announce commit in their own future rounds.
 */
public class BlueFloodTwoPhaseCommit implements BlueFloodNodeListener {

    private static final Logger logger = Logger.getLogger(BlueFloodTwoPhaseCommit.class.getSimpleName());

    private final Queue<Object> proposals;
    private final Queue<VoteValue> votePlan;

    private final Map<Integer, Object> knownProposals = new HashMap<>();
    private final Map<Integer, Map<Integer, VoteValue>> votesByProposal = new HashMap<>();
    private final Map<Integer, TwoPhaseCommitDecision> decisions = new HashMap<>();
    private final Set<Integer> deliveredMessages = new HashSet<>();

    private int networkSize = 0;
    private Integer lastProposedRound = null;

    public BlueFloodTwoPhaseCommit(Queue<Object> proposals, Queue<VoteValue> votePlan) {
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
        if (ctMessage.isNull() || !(ctMessage.content() instanceof TwoPcPayload payload)) {
            return false;
        }

        int receiverId = selectedPacket.receiver().getId();

        mergeIncomingProposals(payload);
        mergeIncomingVotes(payload);
        mergeIncomingDecisions(payload);
        ensureVotesForKnownProposals(context, receiverId);
        maybeFinalizeProposals();
        recordFinalDecisions(context, receiverId);

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
        int round = resolveRound(context);

        if (lastProposedRound == null || lastProposedRound != round) {
            proposalToSend = proposals.poll();
            if (proposalToSend == null) {
                proposalToSend = "proposal-" + selfId;
            }
            registerProposal(round, proposalToSend);
            lastProposedRound = round;
        }

        ensureVotesForKnownProposals(context, selfId);
        maybeFinalizeProposals();
        recordFinalDecisions(context, selfId);

        Map<Integer, Map<Integer, VoteValue>> outgoingVotes = deepCopyVotes();
        Map<Integer, TwoPhaseCommitDecision> outgoingDecisions = new HashMap<>(decisions);
        Map<Integer, Object> outgoingProposals = new HashMap<>(knownProposals);

        TwoPcPayload payload = new TwoPcPayload(selfId, outgoingProposals, outgoingVotes, outgoingDecisions);
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

    private void registerProposal(int proposalId, Object proposal) {
        if (proposal == null || knownProposals.containsKey(proposalId)) {
            return;
        }

        knownProposals.put(proposalId, proposal);
        decisions.putIfAbsent(proposalId, TwoPhaseCommitDecision.IN_PROGRESS);
    }

    private void mergeIncomingVotes(TwoPcPayload payload) {
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
                decisions.putIfAbsent(proposalOwner, TwoPhaseCommitDecision.IN_PROGRESS);
            });
        });
    }

    private void mergeIncomingProposals(TwoPcPayload payload) {
        if (payload.proposals() == null || payload.proposals().isEmpty()) {
            return;
        }
        payload.proposals().forEach(this::registerProposal);
    }

    private void mergeIncomingDecisions(TwoPcPayload payload) {
        if (payload.decisions() == null) {
            return;
        }

        payload.decisions().forEach((proposalOwner, decision) -> {
            decisions.merge(proposalOwner, decision, (oldDecision, newDecision) -> {
                if (oldDecision == null) {
                    return newDecision;
                }
                if (oldDecision == TwoPhaseCommitDecision.IN_PROGRESS) {
                    return newDecision;
                }
                return oldDecision;
            });
        });
    }

    private void ensureVotesForKnownProposals(ContextView context, int selfId) {
        knownProposals.keySet().forEach(proposalOwner -> {
            Map<Integer, VoteValue> perProposal = votesByProposal.computeIfAbsent(proposalOwner, key -> new HashMap<>());
            if (!perProposal.containsKey(selfId)) {
                VoteValue vote = nextVote();
                perProposal.put(selfId, vote);
                recordPhaseVote(context, selfId, proposalOwner);
            }
        });
    }

    private VoteValue nextVote() {
        VoteValue vote = votePlan.poll();
        return vote != null ? vote : VoteValue.YES;
    }

    private void maybeFinalizeProposals() {
        knownProposals.keySet().forEach(proposalId -> {
            TwoPhaseCommitDecision currentDecision = decisions.get(proposalId);
            if (currentDecision != null && currentDecision != TwoPhaseCommitDecision.IN_PROGRESS) {
                return;
            }

            Map<Integer, VoteValue> votes = votesByProposal.get(proposalId);
            if (votes == null || votes.size() < networkSize) {
                return;
            }

            boolean anyNo = votes.values().stream().anyMatch(vote -> vote == VoteValue.NO);
            boolean anyUndecided = votes.values().stream().anyMatch(vote -> vote == VoteValue.UNDECIDED);

            if (anyUndecided) {
                return;
            }

            TwoPhaseCommitDecision finalDecision = anyNo ? TwoPhaseCommitDecision.ABORT
                    : TwoPhaseCommitDecision.COMMIT;

            decisions.put(proposalId, finalDecision);
            logger.log(Level.INFO,
                    "Node[" + proposalId + "] finalized proposal as " + finalDecision + " with votes=" + votes);
        });
    }

    private int resolveRound(ContextView context) {
        if (context == null || context.getApplication().getNetworkTime() == null) {
            return 0;
        }
        return context.getApplication().getNetworkTime().round();
    }

    private void recordPhaseVote(ContextView context, int nodeId, int proposalId) {
        MetricsCollector metrics = resolveMetrics(context);
        if (metrics != null) {
            metrics.recordPhaseTime(proposalId, nodeId, "VOTE", context.getTime());
        }
    }

    private void recordFinalDecisions(ContextView context, int nodeId) {
        MetricsCollector metrics = resolveMetrics(context);
        if (metrics == null) {
            return;
        }
        decisions.forEach((proposalId, decision) -> {
            if (decision == TwoPhaseCommitDecision.COMMIT || decision == TwoPhaseCommitDecision.ABORT) {
                metrics.recordDecisionEnd(proposalId, nodeId, context.getTime(),
                        decision == TwoPhaseCommitDecision.COMMIT);
                metrics.recordPhaseTime(proposalId, nodeId,
                        decision == TwoPhaseCommitDecision.COMMIT ? "COMMIT" : "ABORT",
                        context.getTime());
            }
        });
    }

    private MetricsCollector resolveMetrics(ContextView context) {
        if (context == null) {
            return null;
        }
        return context.getApplication() instanceof MetricsEmitter emitter ? emitter.getMetricsCollector() : null;
    }

    private Map<Integer, Map<Integer, VoteValue>> deepCopyVotes() {
        Map<Integer, Map<Integer, VoteValue>> copy = new HashMap<>();
        votesByProposal.forEach((proposalOwner, votes) -> copy.put(proposalOwner, new HashMap<>(votes)));
        return copy;
    }
}
