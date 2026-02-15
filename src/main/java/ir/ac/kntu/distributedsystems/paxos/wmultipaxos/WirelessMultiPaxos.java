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
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

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
    private final Set<Integer> startedSlots = new HashSet<>();

    private int nodeId = -1;
    private int networkSize = 0;
    private long proposalEpoch = 0;
    private int minProposal = 0;
    private int currentLeaderProposal = -1;
    private int lastProposedSlot = -1;
    private boolean preparing = false;
    private boolean prepared = false;
    private boolean localNoProposal = false;

    public WirelessMultiPaxos(Queue<Object> proposalValues) {
        this.proposalValues = proposalValues != null ? proposalValues : new LinkedList<>();
        this.localNoProposal = this.proposalValues.isEmpty();
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
        updateLocalNoProposal();

        if (self.equals(initiator)) {
            int proposalNumber = nextProposalNumberAbove(minProposal);
            minProposal = proposalNumber;
            currentLeaderProposal = proposalNumber;
            preparing = true;
            prepared = false;
            payload = buildPreparePayload(proposalNumber, 0, Set.of());
            logger.info(String.format("Node[%d] starting wireless multi-Paxos with n=%d", self.getId(),
                    proposalNumber));
        } else {
            payload = WirelessMultiPaxosPayload.empty(MESSAGE_SLOT_CAPACITY);
        }
        payload = attachNoProposal(payload, self.getId());

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

        WirelessMultiPaxosPayload augmentedPayload = augmentWithLocalState(context, receiver, mergedPayload,
                participants);

        WirelessMultiPaxosPayload advancedPayload = maybeAdvanceLeader(context, receiver,
                currentKnowledge.initiator(), augmentedPayload, participants);
        WirelessMultiPaxosPayload finalPayload = attachNoProposal(advancedPayload, receiver.getId());

        boolean attemptChanged = !sameAttempt(mergedPayload, advancedPayload);
        FlagField finalFlags = attemptChanged
                ? FlagField.initial(receiver.getId(), ParticipationFlag.PARTICIPATED)
                : mergedFlags;

        ChaosMessage mergedContent = new ChaosMessage(finalFlags, finalPayload);
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

    private WirelessMultiPaxosPayload augmentWithLocalState(ContextView context, StatefulNode receiver,
            WirelessMultiPaxosPayload payload, int participants) {
        if (payload.phase() == WirelessPaxosPhase.PREPARE) {
            if (payload.proposalNumber() >= 0) {
                minProposal = Math.max(minProposal, payload.proposalNumber());
            }
            List<LogEntry> updated = new ArrayList<>(payload.entries());
            for (int slot = payload.slotStart(); slot < payload.slotStart()
                    + Math.max(1, payload.slotCount()); slot++) {
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
            int nodeId = receiver != null ? receiver.getId() : this.nodeId;
            markDecided(context, nodeId, updatedPayload);
        }
        return updatedPayload;
    }

    private WirelessMultiPaxosPayload maybeAdvanceLeader(ContextView context, StatefulNode receiver, CtNode initiator,
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
                return buildPreparePayload(newer, 0, payload.noProposalNodes());
            }
            if (payload.proposalNumber() >= 0 && participants >= quorum()) {
                applyPrepareLearnings(payload);
                // After learning a chunk, immediately write it back via ACCEPT for the same
                // slots.
                return buildAcceptPayloadForChunk(context, initiator, payload, payload.noProposalNodes());
            }
            return payload;
        }

        if (payload.minProposal() > payload.proposalNumber()) {
            int newer = nextProposalNumberAbove(payload.minProposal());
            minProposal = Math.max(minProposal, newer);
            currentLeaderProposal = newer;
            preparing = true;
            prepared = false;
            return buildPreparePayload(newer, 0, payload.noProposalNodes());
        }

        if (participants >= quorum() && payload.proposalNumber() == currentLeaderProposal) {
            if (preparing) {
                // Finished accepting the prepare chunk: mark synced and move to steady-state
                // accept.
                preparing = false;
                prepared = true;
                lastProposedSlot = Math.max(lastProposedSlot,
                        payload.slotStart() + Math.max(1, payload.slotCount()) - 1);
            }
            int nextSlot = findNextSlotToPropose();
            if (nextSlot < 0) {
                return payload;
            }
            return buildAcceptPayload(context, initiator, payload.proposalNumber(), nextSlot,
                    payload.noProposalNodes());
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

    private WirelessMultiPaxosPayload buildPreparePayload(int proposalNumber, int slotStart,
            Set<Integer> noProposalNodes) {
        List<LogEntry> entries = new ArrayList<>();
        for (int slot = slotStart; slot < Math.min(slotStart + MESSAGE_SLOT_CAPACITY, LOG_CAPACITY); slot++) {
            LogSlot local = log.get(slot);
            if (local != null && local.acceptedProposal >= 0) {
                entries.add(new LogEntry(slot, local.acceptedProposal, local.acceptedValue, null));
            } else {
                entries.add(new LogEntry(slot, -1, null, null));
            }
        }
        return WirelessMultiPaxosPayload.prepare(proposalNumber, slotStart, entries, minProposal, noProposalNodes);
    }

    private WirelessMultiPaxosPayload buildAcceptPayload(ContextView context, CtNode initiator,
            int proposalNumber, int startSlot,
            Set<Integer> noProposalNodes) {
        if (startSlot < 0 || startSlot >= LOG_CAPACITY) {
            return WirelessMultiPaxosPayload.accept(proposalNumber, startSlot, List.of(), minProposal, noProposalNodes);
        }

        List<LogEntry> entries = new ArrayList<>();
        int slot = startSlot;
        while (entries.size() < MESSAGE_SLOT_CAPACITY && slot < LOG_CAPACITY) {
            if (log.containsKey(slot) && log.get(slot).decided) {
                slot++;
                continue;
            }
            Object value = determineValueForSlot(slot);
            if (value == null) {
                slot++;
                continue;
            }
            acceptLocally(slot, proposalNumber, value);
            LogSlot local = log.get(slot);
            entries.add(new LogEntry(slot, local.acceptedProposal, local.acceptedValue, value));
            lastProposedSlot = Math.max(lastProposedSlot, slot);
            slot++;
        }

        if (entries.isEmpty()) {
            return WirelessMultiPaxosPayload.accept(proposalNumber, startSlot, List.of(), minProposal, noProposalNodes);
        }

        recordSlotStarts(context, initiator, entries);

        int slotStart = entries.get(0).slotIndex();
        return WirelessMultiPaxosPayload.accept(proposalNumber, slotStart, entries, minProposal, noProposalNodes);
    }

    private WirelessMultiPaxosPayload buildAcceptPayloadForChunk(ContextView context, CtNode initiator,
            WirelessMultiPaxosPayload preparePayload,
            Set<Integer> noProposalNodes) {
        List<LogEntry> entries = new ArrayList<>();
        for (LogEntry entry : preparePayload.entries()) {
            Object value = entry.acceptedValue() != null ? entry.acceptedValue() : nextProposalValue(null);
            if (value == null) {
                continue;
            }
            entries.add(new LogEntry(entry.slotIndex(), preparePayload.proposalNumber(), value, value));
        }
        // If no entries were present (unlikely), fall back to a single slot accept at
        // the cursor.
        if (entries.isEmpty()) {
            int slot = preparePayload.slotStart();
            Object value = nextProposalValue(null);
            if (value != null) {
                entries.add(new LogEntry(slot, preparePayload.proposalNumber(), value, value));
            }
        }
        if (entries.isEmpty()) {
            return WirelessMultiPaxosPayload.accept(preparePayload.proposalNumber(),
                    preparePayload.slotStart(), List.of(), minProposal, noProposalNodes);
        }
        recordSlotStarts(context, initiator, entries);
        return WirelessMultiPaxosPayload.accept(preparePayload.proposalNumber(),
                preparePayload.slotStart(), entries, minProposal, noProposalNodes);
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

    private void markDecided(ContextView context, int nodeId, WirelessMultiPaxosPayload payload) {
        for (LogEntry entry : payload.entries()) {
            LogSlot slot = log.computeIfAbsent(entry.slotIndex(), LogSlot::new);
            if (entry.acceptedProposal() >= slot.acceptedProposal && entry.acceptedValue() != null) {
                slot.acceptedProposal = entry.acceptedProposal();
                slot.acceptedValue = entry.acceptedValue();
                slot.decided = true;
                MetricsCollector metrics = resolveMetrics(context);
                if (metrics != null) {
                    metrics.recordDecisionEnd(entry.slotIndex(), nodeId, context.getTime(), true);
                    metrics.recordPhaseTime(entry.slotIndex(), nodeId, "DECIDE", context.getTime());
                }
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

    private void updateLocalNoProposal() {
        if (!localNoProposal && proposalValues.isEmpty()) {
            localNoProposal = true;
        }
    }

    private WirelessMultiPaxosPayload attachNoProposal(WirelessMultiPaxosPayload payload, int nodeId) {
        if (payload == null) {
            return null;
        }
        updateLocalNoProposal();
        if (!localNoProposal) {
            return payload;
        }
        Set<Integer> merged = new TreeSet<>(payload.noProposalNodes());
        merged.add(nodeId);
        return payload.withNoProposalNodes(merged);
    }

    private void ensureNetworkInfo(ContextView context, CtNode self) {
        if (context != null && networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
        }
        if (self != null && nodeId < 0) {
            nodeId = self.getId();
        }
    }

    private void recordSlotStarts(ContextView context, CtNode initiator, List<LogEntry> entries) {
        if (context == null || entries == null || entries.isEmpty()) {
            return;
        }
        MetricsCollector metrics = resolveMetrics(context);
        if (metrics == null) {
            return;
        }
        int initiatorId = initiator != null ? initiator.getId() : nodeId;
        for (LogEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            int slotIndex = entry.slotIndex();
            if (startedSlots.add(slotIndex)) {
                metrics.recordDecisionStart(slotIndex, initiatorId, context.getTime());
            }
        }
    }

    private MetricsCollector resolveMetrics(ContextView context) {
        if (context == null) {
            return null;
        }
        return context.getApplication() instanceof MetricsEmitter emitter ? emitter.getMetricsCollector() : null;
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
            localNoProposal = true;
            return null;
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

    private boolean hasUndecidedSlots() {
        for (LogSlot slot : log.values()) {
            if (slot != null && slot.acceptedProposal >= 0 && !slot.decided) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldSleep(WirelessMultiPaxosPayload payload) {
        if (payload == null || networkSize <= 0) {
            return false;
        }
        return (payload.noProposalNodes().size() >= ((networkSize / 2) + 1)) && !hasUndecidedSlots();
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
