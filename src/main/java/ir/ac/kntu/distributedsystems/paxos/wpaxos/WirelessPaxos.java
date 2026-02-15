package ir.ac.kntu.distributedsystems.paxos.wpaxos;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.FlagField;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.aggregation.ParticipationFlag;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * Wireless Paxos implementation inspired by "Paxos Made Wireless".
 * Each node plays proposer, acceptor and learner roles simultaneously.
 */
public class WirelessPaxos implements ChaosNodeListener {
    private static final Logger logger = Logger.getLogger(WirelessPaxos.class.getSimpleName());
    private static final int PROPOSAL_STRIDE = 1024;
    private static final int IDLE_TIMEOUT_SLOTS = 5;

    private final Queue<Object> proposalValues;
    private final int maxRecoveries;

    private int nodeId = -1;
    private int networkSize = 0;
    private long proposalEpoch = 0;

    private int minProposal = 0;
    private int acceptedProposal = -1;
    private Object acceptedValue = null;

    private Object activeValue = null;
    private long lastProgressTime = Long.MIN_VALUE;
    private int remainingRecoveries;

    public WirelessPaxos(Queue<Object> proposalValues) {
        this(proposalValues, 1);
    }

    public WirelessPaxos(Queue<Object> proposalValues, int maxRecoveries) {
        this.proposalValues = proposalValues != null ? proposalValues : new LinkedList<>();
        this.maxRecoveries = Math.max(0, maxRecoveries);
        this.remainingRecoveries = this.maxRecoveries;
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets,
                                     FloodPacket<?> selectedPacket, boolean areSimilar) {
        ensureNetworkInfo(context, selectedPacket.receiver());
        return true;
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        if (!packets.isEmpty()) {
            logger.warning("Wireless Paxos packet lost at node " + packets.get(0).receiver().getId());
        }
    }

    @Override
    public CtMessage<ChaosMessage> initiateMessage(ContextView context, CtNode self, CtNode initiator) {
        ensureNetworkInfo(context, self);
        resetForNewRound(context);

        WirelessPaxosPayload payload;
        if (self.equals(initiator)) {
            int proposalNumber = nextProposalNumberAbove(minProposal);
            activeValue = nextProposalValue(self);
            trackPromise(proposalNumber);
            payload = WirelessPaxosPayload.prepare(proposalNumber, acceptedValue, acceptedProposal);
            logger.info(String.format("Node[%d] starting wireless Paxos with n=%d", self.getId(), proposalNumber));
        } else {
            payload = WirelessPaxosPayload.empty();
        }

        FlagField initialFlags = FlagField.initial(self.getId(), ParticipationFlag.PARTICIPATED);
        return new CtMessage<>(initiator, new ChaosMessage(initialFlags, payload));
    }

    @Override
    public CtMessage<?> getRoundMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return null;
    }

    @Override
    public CtMessage<ChaosMessage> merge(ContextView context, FloodPacket<?> receivedPacket) {
        StatefulNode receiver = (StatefulNode) receivedPacket.receiver();
        ensureNetworkInfo(context, receiver);

        CtMessage<ChaosMessage> currentKnowledge = receiver.getKnowledge();
        CtMessage<ChaosMessage> receivedKnowledge = (CtMessage<ChaosMessage>) receivedPacket.ctMessage();

        WirelessPaxosPayload currentPayload = (WirelessPaxosPayload) currentKnowledge.content().payload();
        WirelessPaxosPayload incomingPayload = (WirelessPaxosPayload) receivedKnowledge.content().payload();

        WirelessPaxosPayload mergedPayload = WirelessPaxosPayload.latest(currentPayload, incomingPayload);
        if (mergedPayload == null) {
            mergedPayload = WirelessPaxosPayload.empty();
        }

        FlagField participationFlags = buildParticipationFlags(currentKnowledge, receivedKnowledge, receiver,
                mergedPayload, currentPayload, incomingPayload);

        WirelessPaxosPayload augmentedPayload = augmentWithLocalState(mergedPayload);

        WirelessPaxosPayload advancedPayload = maybeAdvancePhase(receiver, currentKnowledge.initiator(),
                augmentedPayload, participationFlags.getParticipationCount());

        boolean attemptChanged = !sameAttempt(mergedPayload, advancedPayload);
        FlagField finalFlags = attemptChanged
                ? FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED)
                : participationFlags;

        ChaosMessage mergedContent = new ChaosMessage(finalFlags, advancedPayload);
        CtMessage<ChaosMessage> merged = new CtMessage<>(currentKnowledge.initiator(), mergedContent);
        if (!Objects.equals(currentKnowledge.content(), merged.content())) {
            lastProgressTime = context.getTime();
        }
        return merged;
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return null;
    }

    public boolean shouldEndByIdle(ContextView context) {
        if (context == null || lastProgressTime == Long.MIN_VALUE) {
            return false;
        }
        return context.getTime() - lastProgressTime >= IDLE_TIMEOUT_SLOTS;
    }

    public boolean consumeRecovery(ContextView context) {
        if (remainingRecoveries <= 0) {
            return false;
        }
        remainingRecoveries--;
        if (context != null) {
            lastProgressTime = context.getTime();
        }
        return true;
    }

    private FlagField buildParticipationFlags(CtMessage<ChaosMessage> currentKnowledge,
                                              CtMessage<ChaosMessage> receivedKnowledge,
                                              StatefulNode receiver,
                                              WirelessPaxosPayload mergedPayload,
                                              WirelessPaxosPayload currentPayload,
                                              WirelessPaxosPayload incomingPayload) {
        FlagField flags = FlagField.empty();
        if (sameAttempt(mergedPayload, currentPayload)) {
            flags = flags.merge(currentKnowledge.content().flags());
        }
        if (sameAttempt(mergedPayload, incomingPayload)) {
            flags = flags.merge(receivedKnowledge.content().flags());
        }
        return flags.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
    }

    private WirelessPaxosPayload augmentWithLocalState(WirelessPaxosPayload payload) {
        if (payload.phase() == WirelessPaxosPhase.PREPARE) {
            trackPromise(payload.proposalNumber());
            if (acceptedProposal >= 0 && acceptedValue != null) {
                boolean shouldReplace = payload.acceptedProposal() < acceptedProposal;
                shouldReplace |= payload.acceptedProposal() == acceptedProposal && payload.value() == null;
                if (shouldReplace) {
                    payload = payload.withAcceptedProposal(acceptedValue, acceptedProposal);
                }
            }
        } else {
            if (payload.value() != null && payload.proposalNumber() >= 0) {
                acceptLocally(payload.proposalNumber(), payload.value());
            }
            int updatedMin = Math.max(minProposal, payload.minimumProposal());
            payload = payload.withMinimumProposal(updatedMin);
        }
        return payload;
    }

    private WirelessPaxosPayload maybeAdvancePhase(StatefulNode receiver, CtNode initiator,
                                                   WirelessPaxosPayload payload, int participants) {
        if (initiator == null || receiver.getId() != initiator.getId()) {
            return payload;
        }

        int quorum = Math.max(1, (networkSize / 2) + 1);

        if (payload.phase() == WirelessPaxosPhase.PREPARE) {
            if (participants >= quorum && payload.proposalNumber() >= 0) {
                Object candidateValue = payload.value();
                if (payload.acceptedProposal() >= 0 && candidateValue != null) {
                    activeValue = candidateValue;
                }
                if (activeValue == null) {
                    activeValue = nextProposalValue(receiver);
                }
                int minForAccept = Math.max(minProposal, payload.proposalNumber());
                return WirelessPaxosPayload.accept(payload.proposalNumber(), activeValue, minForAccept);
            }
        } else {
            if (payload.minimumProposal() > payload.proposalNumber()) {
                int newer = nextProposalNumberAbove(payload.minimumProposal());
                logger.info(String.format("Proposer[%d] restarting with n=%d", receiver.getId(), newer));
                trackPromise(newer);
                return WirelessPaxosPayload.prepare(newer, acceptedValue, acceptedProposal);
            }
        }
        return payload;
    }

    private boolean sameAttempt(WirelessPaxosPayload left, WirelessPaxosPayload right) {
        if (left == null || right == null) {
            return false;
        }
        return left.phase() == right.phase() && left.proposalNumber() == right.proposalNumber();
    }

    private void trackPromise(int proposalNumber) {
        if (proposalNumber >= 0) {
            minProposal = Math.max(minProposal, proposalNumber);
        }
    }

    private void acceptLocally(int proposalNumber, Object value) {
        if (proposalNumber < 0 || value == null) {
            return;
        }
        if (proposalNumber >= minProposal) {
            minProposal = proposalNumber;
            acceptedProposal = proposalNumber;
            acceptedValue = value;
        }
    }

    private void ensureNetworkInfo(ContextView context, CtNode self) {
        if (context != null && networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
        }
        if (self != null && nodeId < 0) {
            nodeId = self.getId();
        }
    }

    private int nextProposalNumberAbove(int minExclusive) {
        ensureProposalStride();
        int candidate;
        do {
            proposalEpoch++;
            candidate = (int) (proposalEpoch * PROPOSAL_STRIDE + nodeId);
        } while (candidate <= minExclusive);
        return candidate;
    }

    private void ensureProposalStride() {
        if (nodeId < 0) {
            nodeId = 0;
        }
    }

    private Object nextProposalValue(CtNode self) {
        Object value = proposalValues.poll();
        if (value == null) {
            value = "VAL_" + (self != null ? self.getId() : nodeId) + "_" + System.currentTimeMillis();
        }
        return value;
    }

    private void resetForNewRound(ContextView context) {
        lastProgressTime = context != null ? context.getTime() : Long.MIN_VALUE;
        remainingRecoveries = maxRecoveries;
    }
}
