package ir.ac.kntu.distributedsystems.a2.wmultipaxos;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.FlagField;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.aggregation.ParticipationFlag;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.logging.Logger;

public class WirelessMultiPaxos implements ChaosNodeListener {
    private static final Logger logger = Logger.getLogger(WirelessMultiPaxos.class.getSimpleName());

    private static final int PROPOSAL_STRIDE = 1024;
    private static final int IDLE_TIMEOUT_SLOTS = 5;

    private final Queue<Object> proposals;
    private final int maxRecoveries;

    private int nodeId = -1;
    private int networkSize = 0;
    private long proposalEpoch = 0;

    private int minProposal = 0;
    private int acceptedProposal = -1;
    private Object acceptedValue = null;
    private int currentInstance = 0;
    private long lastProgressTime = Long.MIN_VALUE;
    private int remainingRecoveries;

    public WirelessMultiPaxos(Queue<Object> proposals) {
        this(proposals, 1);
    }

    public WirelessMultiPaxos(Queue<Object> proposals, int maxRecoveries) {
        this.proposals = proposals;
        this.maxRecoveries = Math.max(0, maxRecoveries);
        this.remainingRecoveries = this.maxRecoveries;
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
        ensureNetworkInfo(context, self);
        resetForNewRound(context);

        WirelessMultiPaxosPayload payload;
        if (self.equals(initiator)) {
            int proposalNumber = nextProposalNumberAbove(minProposal);
            payload = WirelessMultiPaxosPayload.prepare(
                    proposalNumber, currentInstance, minProposal, acceptedProposal, acceptedValue);
        } else {
            payload = WirelessMultiPaxosPayload.prepare(
                    -1, currentInstance, minProposal, acceptedProposal, acceptedValue);
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
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(receivedPacket, "receivedPacket");

        StatefulNode receiver = (StatefulNode) receivedPacket.receiver();
        ensureNetworkInfo(context, receiver);

        CtMessage<ChaosMessage> currentKnowledge = receiver.getKnowledge();
        CtMessage<ChaosMessage> receivedKnowledge = (CtMessage<ChaosMessage>) receivedPacket.ctMessage();

        WirelessMultiPaxosPayload currentPayload = payloadOrDefault(currentKnowledge);
        WirelessMultiPaxosPayload receivedPayload = payloadOrDefault(receivedKnowledge);

        CtMessage<ChaosMessage> merged;
        if (currentPayload.done() || receivedPayload.done()) {
            merged = handleDonePhase(receiver, currentKnowledge, receivedKnowledge, currentPayload, receivedPayload);
        } else if (currentPayload.phase() == WirelessMultiPaxosPhase.ACCEPT
                || receivedPayload.phase() == WirelessMultiPaxosPhase.ACCEPT) {
            merged = handleAcceptPhase(context, receiver, currentKnowledge, receivedKnowledge,
                    currentPayload, receivedPayload);
        } else {
            merged = handlePreparePhase(context, receiver, currentKnowledge, receivedKnowledge,
                    currentPayload, receivedPayload);
        }

        if (!Objects.equals(currentKnowledge.content(), merged.content())) {
            onProgress(context);
        }
        return merged;
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return receivedMessage;
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

    public boolean shouldFinalize(WirelessMultiPaxosPayload payload, FlagField flags) {
        if (payload == null || flags == null) {
            return false;
        }
        if (!payload.done()) {
            return false;
        }
        return flags.getParticipationCount() >= quorum() && payload.minProposal() <= payload.proposalNumber();
    }

    private CtMessage<ChaosMessage> handleDonePhase(StatefulNode receiver,
                                                    CtMessage<ChaosMessage> currentKnowledge,
                                                    CtMessage<ChaosMessage> receivedKnowledge,
                                                    WirelessMultiPaxosPayload currentPayload,
                                                    WirelessMultiPaxosPayload receivedPayload) {
        WirelessMultiPaxosPayload dominant = currentPayload.done() ? currentPayload : receivedPayload;
        WirelessMultiPaxosPayload mergedPayload = mergePayloads(currentPayload, receivedPayload, dominant);
        WirelessMultiPaxosPayload donePayload = WirelessMultiPaxosPayload.accept(
                mergedPayload.proposalNumber(),
                mergedPayload.instanceId(),
                mergedPayload.minProposal(),
                mergedPayload.acceptedProposal(),
                mergedPayload.acceptedValue(),
                mergedPayload.value(),
                true);
        FlagField mergedFlags = mergeAttemptFlags(currentKnowledge, receivedKnowledge,
                currentPayload, receivedPayload, donePayload, receiver);
        return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(mergedFlags, donePayload));
    }

    private CtMessage<ChaosMessage> handlePreparePhase(ContextView context,
                                                       StatefulNode receiver,
                                                       CtMessage<ChaosMessage> currentKnowledge,
                                                       CtMessage<ChaosMessage> receivedKnowledge,
                                                       WirelessMultiPaxosPayload currentPayload,
                                                       WirelessMultiPaxosPayload receivedPayload) {
        int proposalNumber = Math.max(currentPayload.proposalNumber(), receivedPayload.proposalNumber());
        int instanceId = Math.max(currentPayload.instanceId(), receivedPayload.instanceId());
        syncInstance(instanceId);

        if (proposalNumber > minProposal) {
            minProposal = proposalNumber;
        }

        WirelessMultiPaxosPayload targetPayload = WirelessMultiPaxosPayload.prepare(
                proposalNumber, instanceId, minProposal, acceptedProposal, acceptedValue);
        FlagField mergedFlags = mergeAttemptFlags(currentKnowledge, receivedKnowledge,
                currentPayload, receivedPayload, targetPayload, receiver);

        WirelessMultiPaxosPayload basePayload = mergePayloads(currentPayload, receivedPayload, targetPayload);

        if (isLeader(receiver, receivedKnowledge) && mergedFlags.getParticipationCount() >= quorum()) {
            Object chosenValue = basePayload.acceptedProposal() >= 0 ? basePayload.acceptedValue() : nextProposal();
            if (chosenValue == null) {
                WirelessMultiPaxosPayload donePayload = WirelessMultiPaxosPayload.accept(
                        proposalNumber, instanceId, basePayload.minProposal(),
                        basePayload.acceptedProposal(), basePayload.acceptedValue(), null, true);
            FlagField reset = FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED);
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(reset, donePayload));
        }

            WirelessMultiPaxosPayload acceptPayload = WirelessMultiPaxosPayload.accept(
                    proposalNumber, instanceId, basePayload.minProposal(),
                    basePayload.acceptedProposal(), basePayload.acceptedValue(), chosenValue, false);
            FlagField reset = FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED);
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(reset, acceptPayload));
        }

        WirelessMultiPaxosPayload preparePayload = WirelessMultiPaxosPayload.prepare(
                proposalNumber, instanceId, basePayload.minProposal(),
                basePayload.acceptedProposal(), basePayload.acceptedValue());
        return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(mergedFlags, preparePayload));
    }

    private CtMessage<ChaosMessage> handleAcceptPhase(ContextView context,
                                                      StatefulNode receiver,
                                                      CtMessage<ChaosMessage> currentKnowledge,
                                                      CtMessage<ChaosMessage> receivedKnowledge,
                                                      WirelessMultiPaxosPayload currentPayload,
                                                      WirelessMultiPaxosPayload receivedPayload) {
        WirelessMultiPaxosPayload dominant = dominantPayload(currentPayload, receivedPayload);
        int proposalNumber = dominant.proposalNumber();
        int instanceId = dominant.instanceId();
        syncInstance(instanceId);
        Object value = dominant.value();
        boolean done = dominant.done();

        if (!done && value != null && proposalNumber >= minProposal) {
            minProposal = proposalNumber;
            acceptedProposal = proposalNumber;
            acceptedValue = value;
        }

        WirelessMultiPaxosPayload targetPayload = new WirelessMultiPaxosPayload(
                WirelessMultiPaxosPhase.ACCEPT, proposalNumber, instanceId,
                minProposal, acceptedProposal, acceptedValue, value, done);
        FlagField mergedFlags = mergeAttemptFlags(currentKnowledge, receivedKnowledge,
                currentPayload, receivedPayload, targetPayload, receiver);

        WirelessMultiPaxosPayload basePayload = mergePayloads(currentPayload, receivedPayload, targetPayload);

        if (isLeader(receiver, receivedKnowledge)
                && mergedFlags.getParticipationCount() >= quorum()
                && basePayload.minProposal() <= proposalNumber
                && !done) {
            currentInstance = Math.max(currentInstance, instanceId) + 1;
            acceptedProposal = -1;
            acceptedValue = null;
            Object next = nextProposal();
            FlagField reset = FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED);
            if (next == null) {
                WirelessMultiPaxosPayload donePayload = WirelessMultiPaxosPayload.accept(
                        proposalNumber, currentInstance, basePayload.minProposal(),
                        basePayload.acceptedProposal(), basePayload.acceptedValue(), null, true);
                return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(reset, donePayload));
            }
            WirelessMultiPaxosPayload nextPayload = WirelessMultiPaxosPayload.accept(
                    proposalNumber, currentInstance, basePayload.minProposal(),
                    basePayload.acceptedProposal(), basePayload.acceptedValue(), next, false);
            return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(reset, nextPayload));
        }

        WirelessMultiPaxosPayload acceptPayload = WirelessMultiPaxosPayload.accept(
                proposalNumber, instanceId, basePayload.minProposal(),
                basePayload.acceptedProposal(), basePayload.acceptedValue(), value, done);
        return new CtMessage<>(receivedKnowledge.initiator(), new ChaosMessage(mergedFlags, acceptPayload));
    }

    private WirelessMultiPaxosPayload mergePayloads(WirelessMultiPaxosPayload left,
                                                    WirelessMultiPaxosPayload right,
                                                    WirelessMultiPaxosPayload dominant) {
        int min = Math.max(left.minProposal(), right.minProposal());
        min = Math.max(min, minProposal);

        int accepted = (dominant.instanceId() == currentInstance) ? acceptedProposal : -1;
        Object acceptedVal = (dominant.instanceId() == currentInstance) ? acceptedValue : null;
        if (left.instanceId() == dominant.instanceId() && left.acceptedProposal() > accepted) {
            accepted = left.acceptedProposal();
            acceptedVal = left.acceptedValue();
        }
        if (right.instanceId() == dominant.instanceId() && right.acceptedProposal() > accepted) {
            accepted = right.acceptedProposal();
            acceptedVal = right.acceptedValue();
        }

        return new WirelessMultiPaxosPayload(
                dominant.phase(),
                dominant.proposalNumber(),
                dominant.instanceId(),
                min,
                accepted,
                acceptedVal,
                dominant.value(),
                dominant.done());
    }

    private WirelessMultiPaxosPayload dominantPayload(WirelessMultiPaxosPayload left,
                                                      WirelessMultiPaxosPayload right) {
        if (left.phase() != right.phase()) {
            return left.phase() == WirelessMultiPaxosPhase.ACCEPT ? left : right;
        }
        if (left.proposalNumber() != right.proposalNumber()) {
            return left.proposalNumber() > right.proposalNumber() ? left : right;
        }
        if (left.instanceId() != right.instanceId()) {
            return left.instanceId() > right.instanceId() ? left : right;
        }
        return left;
    }

    private boolean isLeader(StatefulNode receiver, CtMessage<ChaosMessage> message) {
        return receiver != null && message != null && receiver.equals(message.initiator());
    }

    private Object nextProposal() {
        return proposals != null ? proposals.poll() : null;
    }

    private int quorum() {
        return (networkSize / 2) + 1;
    }

    private WirelessMultiPaxosPayload payloadOrDefault(CtMessage<ChaosMessage> message) {
        if (message == null || message.content() == null) {
            return WirelessMultiPaxosPayload.prepare(-1, currentInstance, minProposal,
                    acceptedProposal, acceptedValue);
        }
        Object payload = message.content().payload();
        if (payload instanceof WirelessMultiPaxosPayload mpPayload) {
            return mpPayload;
        }
        return WirelessMultiPaxosPayload.prepare(-1, currentInstance, minProposal,
                acceptedProposal, acceptedValue);
    }

    private void ensureNetworkInfo(ContextView context, CtNode self) {
        if (context != null && networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
        }
        if (self != null && nodeId < 0) {
            nodeId = self.getId();
        }
    }

    private void resetForNewRound(ContextView context) {
        onProgress(context);
    }

    private void onProgress(ContextView context) {
        lastProgressTime = context != null ? context.getTime() : Long.MIN_VALUE;
        // Recovery budget is per consecutive timeout streak.
        remainingRecoveries = maxRecoveries;
    }

    private void syncInstance(int instanceId) {
        if (instanceId > currentInstance) {
            currentInstance = instanceId;
            acceptedProposal = -1;
            acceptedValue = null;
        }
    }

    private boolean sameAttempt(WirelessMultiPaxosPayload payload, WirelessMultiPaxosPayload target) {
        if (payload == null || target == null) {
            return false;
        }
        return payload.phase() == target.phase()
                && payload.proposalNumber() == target.proposalNumber()
                && payload.instanceId() == target.instanceId();
    }

    private FlagField mergeAttemptFlags(CtMessage<ChaosMessage> currentKnowledge,
                                        CtMessage<ChaosMessage> receivedKnowledge,
                                        WirelessMultiPaxosPayload currentPayload,
                                        WirelessMultiPaxosPayload receivedPayload,
                                        WirelessMultiPaxosPayload targetPayload,
                                        StatefulNode receiver) {
        FlagField merged = FlagField.empty();
        if (currentKnowledge != null && currentKnowledge.content() != null
                && sameAttempt(currentPayload, targetPayload)) {
            merged = merged.merge(currentKnowledge.content().flags());
        }
        if (receivedKnowledge != null && receivedKnowledge.content() != null
                && sameAttempt(receivedPayload, targetPayload)) {
            merged = merged.merge(receivedKnowledge.content().flags());
        }
        if (receiver != null) {
            merged = merged.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
        }
        return merged;
    }

    private int nextProposalNumberAbove(int minExclusive) {
        if (nodeId < 0) {
            nodeId = 0;
        }
        int candidate;
        do {
            proposalEpoch++;
            candidate = (int) (proposalEpoch * PROPOSAL_STRIDE + nodeId);
        } while (candidate <= minExclusive);
        return candidate;
    }
}
