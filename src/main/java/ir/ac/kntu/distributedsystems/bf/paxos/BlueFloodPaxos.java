package ir.ac.kntu.distributedsystems.bf.paxos;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
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
 * Simplified Paxos-style consensus over BlueFlood:
 * - each node proposes once in its own round,
 * - decisions are reached with majority votes (no need for unanimity),
 * - decisions are propagated with every flood message.
 */
public class BlueFloodPaxos implements BlueFloodNodeListener {

    private static final Logger logger = Logger.getLogger(BlueFloodPaxos.class.getSimpleName());

    private final Queue<Object> proposals;
    private final Queue<VoteValue> votePlan;

    private final Map<Integer, Object> knownProposals = new HashMap<>();
    private final Map<Integer, Map<Integer, VoteValue>> votesByProposal = new HashMap<>();
    private final Map<Integer, PaxosDecision> decisions = new HashMap<>();
    private final Set<Integer> deliveredMessages = new HashSet<>();

    private int networkSize = 0;
    private Integer lastProposedRound = null;

    public BlueFloodPaxos(Queue<Object> proposals, Queue<VoteValue> votePlan) {
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
        if (ctMessage.isNull() || !(ctMessage.content() instanceof PaxosPayload payload)) {
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
        Map<Integer, PaxosDecision> outgoingDecisions = new HashMap<>(decisions);
        Map<Integer, Object> outgoingProposals = new HashMap<>(knownProposals);

        PaxosPayload payload = new PaxosPayload(selfId, outgoingProposals, outgoingVotes, outgoingDecisions);
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
        decisions.putIfAbsent(proposalId, PaxosDecision.IN_PROGRESS);
    }

    private void mergeIncomingProposals(PaxosPayload payload) {
        if (payload.proposals() == null || payload.proposals().isEmpty()) {
            return;
        }
        payload.proposals().forEach(this::registerProposal);
    }

    private void mergeIncomingVotes(PaxosPayload payload) {
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
                decisions.putIfAbsent(proposalOwner, PaxosDecision.IN_PROGRESS);
            });
        });
    }

    private void mergeIncomingDecisions(PaxosPayload payload) {
        if (payload.decisions() == null) {
            return;
        }

        payload.decisions().forEach((proposalOwner, incomingDecision) -> {
            PaxosDecision current = decisions.get(proposalOwner);
            decisions.put(proposalOwner, mergeDecision(current, incomingDecision));
        });
    }

    private PaxosDecision mergeDecision(PaxosDecision current, PaxosDecision incoming) {
        if (incoming == null) {
            return current;
        }
        if (current == null || current == PaxosDecision.IN_PROGRESS) {
            return incoming;
        }
        if (current == PaxosDecision.COMMIT || current == PaxosDecision.ABORT) {
            return current;
        }
        return incoming;
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
        int majority = (networkSize / 2) + 1;

        knownProposals.keySet().forEach(proposalOwner -> {
            PaxosDecision currentDecision = decisions.get(proposalOwner);
            if (currentDecision == PaxosDecision.ABORT || currentDecision == PaxosDecision.COMMIT) {
                return;
            }

            Map<Integer, VoteValue> votes = votesByProposal.get(proposalOwner);
            if (votes == null) {
                return;
            }

            int yesCount = (int) votes.values().stream().filter(v -> v == VoteValue.YES).count();
            int noCount = (int) votes.values().stream().filter(v -> v == VoteValue.NO).count();

            if (noCount >= majority) {
                decisions.put(proposalOwner, PaxosDecision.ABORT);
                logger.log(Level.INFO,
                        "Proposal[" + proposalOwner + "] aborted after majority NO votes");
                return;
            }

            if (yesCount >= majority) {
                decisions.put(proposalOwner, PaxosDecision.COMMIT);
                logger.log(Level.INFO,
                        "Proposal[" + proposalOwner + "] committed after majority YES votes");
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
            if (decision == PaxosDecision.COMMIT || decision == PaxosDecision.ABORT) {
                metrics.recordDecisionEnd(proposalId, nodeId, context.getTime(), decision == PaxosDecision.COMMIT);
                metrics.recordPhaseTime(proposalId, nodeId, "DECIDE", context.getTime());
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
