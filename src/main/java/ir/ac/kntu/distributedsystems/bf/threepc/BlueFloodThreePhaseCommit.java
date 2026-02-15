package ir.ac.kntu.distributedsystems.bf.threepc;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommitDecision;
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
    private Integer lastProposedRound = null;

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
        ensureVotesForKnownProposals(context, receiverId);
        maybeAdvanceProposals();
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
        maybeAdvanceProposals();
        recordFinalDecisions(context, selfId);

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

    private void registerProposal(int proposalId, Object proposal) {
        if (proposal == null || knownProposals.containsKey(proposalId)) {
            return;
        }

        knownProposals.put(proposalId, proposal);
        decisions.putIfAbsent(proposalId, ThreePhaseCommitDecision.IN_PROGRESS);
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

    private void maybeAdvanceProposals() {
        knownProposals.keySet().forEach(proposalId -> {
            ThreePhaseCommitDecision currentDecision = decisions.get(proposalId);
            if (currentDecision == ThreePhaseCommitDecision.ABORT
                    || currentDecision == ThreePhaseCommitDecision.COMMIT) {
                return;
            }

            Map<Integer, VoteValue> votes = votesByProposal.get(proposalId);
            if (votes == null || votes.size() < networkSize) {
                return;
            }

            boolean anyNo = votes.values().stream().anyMatch(vote -> vote == VoteValue.NO);
            boolean anyUndecided = votes.values().stream().anyMatch(vote -> vote == VoteValue.UNDECIDED);

            if (anyNo) {
                decisions.put(proposalId, ThreePhaseCommitDecision.ABORT);
                logger.log(Level.INFO,
                        "Proposal[" + proposalId + "] aborted after receiving a NO vote");
                return;
            }

            if (anyUndecided) {
                return;
            }

            if (currentDecision == null || currentDecision == ThreePhaseCommitDecision.IN_PROGRESS) {
                decisions.put(proposalId, ThreePhaseCommitDecision.PRE_COMMIT);
                preCommitAcks.computeIfAbsent(proposalId, key -> new HashSet<>()).add(proposalId);
                logger.log(Level.INFO, "Proposal[" + proposalId + "] reached PRE_COMMIT");
            } else if (currentDecision == ThreePhaseCommitDecision.PRE_COMMIT) {
                Set<Integer> acks = preCommitAcks.getOrDefault(proposalId, Set.of());
                if (acks.size() < networkSize) {
                    return;
                }
                decisions.put(proposalId, ThreePhaseCommitDecision.COMMIT);
                logger.log(Level.INFO, "Proposal[" + proposalId + "] finalized COMMIT");
            }
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
            if (decision == ThreePhaseCommitDecision.PRE_COMMIT) {
                metrics.recordPhaseTime(proposalId, nodeId, "PRE_COMMIT", context.getTime());
            }
            if (decision == ThreePhaseCommitDecision.COMMIT || decision == ThreePhaseCommitDecision.ABORT) {
                metrics.recordDecisionEnd(proposalId, nodeId, context.getTime(),
                        decision == ThreePhaseCommitDecision.COMMIT);
                metrics.recordPhaseTime(proposalId, nodeId,
                        decision == ThreePhaseCommitDecision.COMMIT ? "COMMIT" : "ABORT",
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
