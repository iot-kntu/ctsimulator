package ir.ac.kntu.distributedsystems.paxos.wmultipaxos;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.FlagField;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.aggregation.ParticipationFlag;
import ir.ac.kntu.distributedsystems.paxos.wmultipaxos.WirelessMultiPaxosPayload.LogEntry;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosPhase;

import java.util.*;
import java.util.logging.Logger;

/**
 * Wireless Multi-Paxos on top of Chaos aggregation.
 * It follows "Paxos Made Wireless" and supports chunked Prepare followed by
 * continuous Accepts for a stable leader.
 */
public class WirelessMultiPaxos implements ChaosNodeListener {
    private static final Logger logger = Logger.getLogger(WirelessMultiPaxos.class.getSimpleName());
    private static final int PROPOSAL_STRIDE = 1024;
    private static final int LOG_CAPACITY = 8;
    private static final int MESSAGE_SLOT_CAPACITY = 4;

    private final Queue<Object> proposalValues;
    private final Map<Integer, LogSlot> log = new HashMap<>();

    private int nodeId = -1;
    private int networkSize = 0;
    private long proposalEpoch = 0;
    private int minProposal = 0;
    private int currentLeaderProposal = -1;
    private int lastProposedSlot = -1;
    private boolean preparing = false;
    private boolean prepared = false;

    public WirelessMultiPaxos(Queue<Object> proposalValues) {
        this.proposalValues = proposalValues != null ? proposalValues : new LinkedList<>();
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket,
                                     boolean areSimilar) {
        ensureNetworkInfo(context, selectedPacket != null ? selectedPacket.receiver() : null);
        return true;
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        if (!packets.isEmpty()) {
            logger.warning("Wireless Multi-Paxos packet lost at node " + packets.get(0).receiver().getId());
        }
    }

    @Override
    public CtMessage<ChaosMessage> initiateMessage(ContextView context, CtNode self, CtNode initiator) {
        ensureNetworkInfo(context, self);
        WirelessMultiPaxosPayload payload;

        if (self.equals(initiator)) {
            int proposalNumber = nextProposalNumberAbove(minProposal);
            minProposal = proposalNumber;
            currentLeaderProposal = proposalNumber;
            preparing = true;
            prepared = false;
            payload = buildPreparePayload(proposalNumber, 0);
            logger.info(String.format("Node[%d] starting wireless multi-Paxos with n=%d", self.getId(),
                    proposalNumber));
        } else {
            payload = WirelessMultiPaxosPayload.empty(MESSAGE_SLOT_CAPACITY);
        }

        FlagField flags = FlagField.initial(self.getId(), ParticipationFlag.PARTICIPATED);
        return new CtMessage<>(initiator, new ChaosMessage(flags, payload));
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
        CtMessage<ChaosMessage> incomingKnowledge = (CtMessage<ChaosMessage>) receivedPacket.ctMessage();

        WirelessMultiPaxosPayload currentPayload = payloadOrEmpty(currentKnowledge);
        WirelessMultiPaxosPayload incomingPayload = payloadOrEmpty(incomingKnowledge);

        WirelessMultiPaxosPayload mergedPayload = WirelessMultiPaxosPayload.mergePayloads(
                currentPayload, incomingPayload, MESSAGE_SLOT_CAPACITY);

        FlagField mergedFlags = mergeFlags(currentKnowledge, incomingKnowledge, currentPayload, incomingPayload,
                mergedPayload, receiver);
        int participants = mergedFlags.getParticipationCount();

        WirelessMultiPaxosPayload augmentedPayload = augmentWithLocalState(mergedPayload, participants);

        WirelessMultiPaxosPayload advancedPayload = maybeAdvanceLeader(receiver, currentKnowledge.initiator(),
                augmentedPayload, participants);

        boolean attemptChanged = !sameAttempt(mergedPayload, advancedPayload);
        FlagField finalFlags = attemptChanged
                ? FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED)
                : mergedFlags;

        ChaosMessage mergedContent = new ChaosMessage(finalFlags, advancedPayload);
        return new CtMessage<>(currentKnowledge.initiator(), mergedContent);
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return null;
    }

    private FlagField mergeFlags(CtMessage<ChaosMessage> currentKnowledge, CtMessage<ChaosMessage> receivedKnowledge,
                                 WirelessMultiPaxosPayload currentPayload, WirelessMultiPaxosPayload incomingPayload,
                                 WirelessMultiPaxosPayload mergedPayload, StatefulNode receiver) {
        FlagField flags = FlagField.empty();
        if (sameAttempt(mergedPayload, currentPayload) && currentKnowledge.content() != null) {
            flags = flags.merge(currentKnowledge.content().flags());
        }
        if (sameAttempt(mergedPayload, incomingPayload) && receivedKnowledge.content() != null) {
            flags = flags.merge(receivedKnowledge.content().flags());
        }
        return flags.merge(FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED));
    }

    private WirelessMultiPaxosPayload augmentWithLocalState(WirelessMultiPaxosPayload payload, int participants) {
        if (payload.phase() == WirelessPaxosPhase.PREPARE) {
            if (payload.proposalNumber() >= 0) {
                minProposal = Math.max(minProposal, payload.proposalNumber());
            }
            List<LogEntry> updated = new ArrayList<>(payload.entries());
            for (int slot = payload.slotStart(); slot < payload.slotStart() + Math.max(1, payload.slotCount()); slot++) {
                LogSlot local = log.get(slot);
                if (local == null || local.acceptedProposal < 0) {
                    continue;
                }
                OptionalInt idx = findIndex(updated, slot);
                LogEntry entry = new LogEntry(slot, local.acceptedProposal, local.acceptedValue, null);
                if (idx.isPresent()) {
                    LogEntry existing = updated.get(idx.getAsInt());
                    if (entry.acceptedProposal() > existing.acceptedProposal()
                            || (entry.acceptedProposal() == existing.acceptedProposal()
                                    && existing.acceptedValue() == null && entry.acceptedValue() != null)) {
                        updated.set(idx.getAsInt(), entry);
                    }
                } else {
                    updated.add(entry);
                }
            }
            int nextMin = Math.max(minProposal, payload.minProposal());
            return payload.withEntries(updated, MESSAGE_SLOT_CAPACITY).withMinProposal(nextMin);
        }

        int updatedMin = Math.max(payload.minProposal(), minProposal);
        if (payload.proposalNumber() >= minProposal) {
            minProposal = payload.proposalNumber();
        } else {
            updatedMin = Math.max(updatedMin, minProposal);
        }

        List<LogEntry> updatedEntries = new ArrayList<>();
        for (LogEntry entry : payload.entries()) {
            if (payload.proposalNumber() >= minProposal && entry.proposedValue() != null) {
                acceptLocally(entry.slotIndex(), payload.proposalNumber(),
                        entry.acceptedValue() != null ? entry.acceptedValue() : entry.proposedValue());
            }
            LogSlot local = log.get(entry.slotIndex());
            if (local != null && local.acceptedProposal >= 0) {
                LogEntry updated = new LogEntry(entry.slotIndex(), local.acceptedProposal, local.acceptedValue(),
                        entry.proposedValue());
                updatedEntries.add(updated);
            } else {
                updatedEntries.add(entry);
            }
        }

        WirelessMultiPaxosPayload updatedPayload = payload.withEntries(updatedEntries, MESSAGE_SLOT_CAPACITY)
                .withMinProposal(updatedMin);
        if (updatedPayload.phase() == WirelessPaxosPhase.ACCEPT && participants >= quorum()
                && updatedPayload.minProposal() <= updatedPayload.proposalNumber()) {
            markDecided(updatedPayload);
        }
        return updatedPayload;
    }

    private WirelessMultiPaxosPayload maybeAdvanceLeader(StatefulNode receiver, CtNode initiator,
                                                         WirelessMultiPaxosPayload payload, int participants) {
        if (initiator == null || receiver.getId() != initiator.getId()) {
            return payload;
        }

        if (payload.phase() == WirelessPaxosPhase.PREPARE) {
            if (payload.minProposal() > payload.proposalNumber()) {
                int newer = nextProposalNumberAbove(payload.minProposal());
                minProposal = Math.max(minProposal, newer);
                currentLeaderProposal = newer;
                preparing = true;
                prepared = false;
                return buildPreparePayload(newer, 0);
            }
            if (payload.proposalNumber() >= 0 && participants >= quorum()) {
                applyPrepareLearnings(payload);
                // After learning a chunk, immediately write it back via ACCEPT for the same slots.
                return buildAcceptPayloadForChunk(payload);
            }
            return payload;
        }

        if (payload.minProposal() > payload.proposalNumber()) {
            int newer = nextProposalNumberAbove(payload.minProposal());
            minProposal = Math.max(minProposal, newer);
            currentLeaderProposal = newer;
            preparing = true;
            prepared = false;
            return buildPreparePayload(newer, 0);
        }

        if (participants >= quorum() && payload.proposalNumber() == currentLeaderProposal) {
            if (preparing) {
                // Finished accepting the prepare chunk: mark synced and move to steady-state accept.
                preparing = false;
                prepared = true;
                lastProposedSlot = Math.max(lastProposedSlot, payload.slotStart() + Math.max(1, payload.slotCount()) - 1);
            }
            int nextSlot = findNextSlotToPropose();
            if (nextSlot < 0) {
                return payload;
            }
            return buildAcceptPayload(payload.proposalNumber(), nextSlot);
        }
        return payload;
    }

    private boolean sameAttempt(WirelessMultiPaxosPayload left, WirelessMultiPaxosPayload right) {
        if (left == null || right == null) {
            return false;
        }
        return left.phase() == right.phase()
                && left.proposalNumber() == right.proposalNumber()
                && left.slotStart() == right.slotStart();
    }

    private void applyPrepareLearnings(WirelessMultiPaxosPayload payload) {
        for (LogEntry entry : payload.entries()) {
            if (entry.acceptedProposal() >= 0 && entry.acceptedValue() != null) {
                LogSlot slot = log.computeIfAbsent(entry.slotIndex(), LogSlot::new);
                if (entry.acceptedProposal() > slot.acceptedProposal) {
                    slot.acceptedProposal = entry.acceptedProposal();
                    slot.acceptedValue = entry.acceptedValue();
                }
            }
        }
    }

    private WirelessMultiPaxosPayload buildPreparePayload(int proposalNumber, int slotStart) {
        List<LogEntry> entries = new ArrayList<>();
        for (int slot = slotStart; slot < Math.min(slotStart + MESSAGE_SLOT_CAPACITY, LOG_CAPACITY); slot++) {
            LogSlot local = log.get(slot);
            if (local != null && local.acceptedProposal >= 0) {
                entries.add(new LogEntry(slot, local.acceptedProposal, local.acceptedValue, null));
            } else {
                entries.add(new LogEntry(slot, -1, null, null));
            }
        }
        return WirelessMultiPaxosPayload.prepare(proposalNumber, slotStart, entries, minProposal);
    }

    private WirelessMultiPaxosPayload buildAcceptPayload(int proposalNumber, int startSlot) {
        if (startSlot < 0 || startSlot >= LOG_CAPACITY) {
            return WirelessMultiPaxosPayload.accept(proposalNumber, startSlot, List.of(), minProposal);
        }

        List<LogEntry> entries = new ArrayList<>();
        int slot = startSlot;
        while (entries.size() < MESSAGE_SLOT_CAPACITY && slot < LOG_CAPACITY) {
            if (log.containsKey(slot) && log.get(slot).decided) {
                slot++;
                continue;
            }
            Object value = determineValueForSlot(slot);
            acceptLocally(slot, proposalNumber, value);
            LogSlot local = log.get(slot);
            entries.add(new LogEntry(slot, local.acceptedProposal, local.acceptedValue, value));
            lastProposedSlot = Math.max(lastProposedSlot, slot);
            slot++;
        }

        if (entries.isEmpty()) {
            return WirelessMultiPaxosPayload.accept(proposalNumber, startSlot, List.of(), minProposal);
        }

        int slotStart = entries.get(0).slotIndex();
        return WirelessMultiPaxosPayload.accept(proposalNumber, slotStart, entries, minProposal);
    }

    private WirelessMultiPaxosPayload buildAcceptPayloadForChunk(WirelessMultiPaxosPayload preparePayload) {
        List<LogEntry> entries = new ArrayList<>();
        for (LogEntry entry : preparePayload.entries()) {
            Object value = entry.acceptedValue() != null ? entry.acceptedValue() : nextProposalValue(null);
            entries.add(new LogEntry(entry.slotIndex(), preparePayload.proposalNumber(), value, value));
        }
        // If no entries were present (unlikely), fall back to a single slot accept at the cursor.
        if (entries.isEmpty()) {
            int slot = preparePayload.slotStart();
            Object value = nextProposalValue(null);
            entries.add(new LogEntry(slot, preparePayload.proposalNumber(), value, value));
        }
        return WirelessMultiPaxosPayload.accept(preparePayload.proposalNumber(),
                preparePayload.slotStart(), entries, minProposal);
    }

    private Object determineValueForSlot(int slotIndex) {
        LogSlot existing = log.get(slotIndex);
        if (existing != null && existing.acceptedProposal >= 0 && existing.acceptedValue != null) {
            return existing.acceptedValue;
        }
        return nextProposalValue(null);
    }

    private void acceptLocally(int slotIndex, int proposalNumber, Object value) {
        if (proposalNumber < 0 || value == null || slotIndex < 0 || slotIndex >= LOG_CAPACITY) {
            return;
        }
        if (proposalNumber >= minProposal) {
            minProposal = proposalNumber;
            LogSlot slot = log.computeIfAbsent(slotIndex, LogSlot::new);
            if (proposalNumber >= slot.acceptedProposal) {
                slot.acceptedProposal = proposalNumber;
                slot.acceptedValue = value;
            }
        }
    }

    private void markDecided(WirelessMultiPaxosPayload payload) {
        for (LogEntry entry : payload.entries()) {
            LogSlot slot = log.computeIfAbsent(entry.slotIndex(), LogSlot::new);
            if (entry.acceptedProposal() >= slot.acceptedProposal && entry.acceptedValue() != null) {
                slot.acceptedProposal = entry.acceptedProposal();
                slot.acceptedValue = entry.acceptedValue();
                slot.decided = true;
            }
        }
    }

    private WirelessMultiPaxosPayload payloadOrEmpty(CtMessage<ChaosMessage> message) {
        if (message == null || message.content() == null) {
            return WirelessMultiPaxosPayload.empty(MESSAGE_SLOT_CAPACITY);
        }
        Object payload = message.content().payload();
        if (payload instanceof WirelessMultiPaxosPayload mpPayload) {
            return mpPayload;
        }
        return WirelessMultiPaxosPayload.empty(MESSAGE_SLOT_CAPACITY);
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

    private int findNextSlotToPropose() {
        int candidate = lastProposedSlot + 1;
        while (candidate < LOG_CAPACITY && log.containsKey(candidate) && log.get(candidate).decided) {
            candidate++;
        }
        if (candidate >= LOG_CAPACITY) {
            return -1;
        }
        return candidate;
    }

    private OptionalInt findIndex(List<LogEntry> entries, int slotIndex) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).slotIndex() == slotIndex) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private int quorum() {
        if (networkSize <= 0) {
            return 1;
        }
        return (networkSize / 2) + 1;
    }

    private static class LogSlot {
        private final int index;
        private int acceptedProposal = -1;
        private Object acceptedValue = null;
        private boolean decided = false;

        private int acceptedProposal() {
            return acceptedProposal;
        }

        private Object acceptedValue() {
            return acceptedValue;
        }

        private boolean decided() {
            return decided;
        }

        private LogSlot(int index) {
            this.index = index;
        }
    }
}
