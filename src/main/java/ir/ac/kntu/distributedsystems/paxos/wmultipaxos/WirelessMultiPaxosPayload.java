package ir.ac.kntu.distributedsystems.paxos.wmultipaxos;

import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosPhase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Payload for Wireless Multi-Paxos messages.
 * Carries a window of log entries plus proposal/meta data so it can be merged
 * in-flight by Chaos aggregation.
 */
public record WirelessMultiPaxosPayload(
        WirelessPaxosPhase phase,
        int proposalNumber,
        int slotStart,
        int slotCount,
        List<LogEntry> entries,
        int minProposal,
        Set<Integer> noProposalNodes) {

    public static WirelessMultiPaxosPayload empty(int slotCapacity) {
        return new WirelessMultiPaxosPayload(WirelessPaxosPhase.PREPARE, -1, 0, Math.max(0, slotCapacity),
                List.of(), 0, Set.of());
    }

    public static WirelessMultiPaxosPayload prepare(int proposalNumber, int slotStart, List<LogEntry> entries,
                                                    int minProposal, Set<Integer> noProposalNodes) {
        return build(WirelessPaxosPhase.PREPARE, proposalNumber, slotStart, entries, minProposal, noProposalNodes);
    }

    public static WirelessMultiPaxosPayload accept(int proposalNumber, int slotStart, List<LogEntry> entries,
                                                   int minProposal, Set<Integer> noProposalNodes) {
        return build(WirelessPaxosPhase.ACCEPT, proposalNumber, slotStart, entries, minProposal, noProposalNodes);
    }

    private static WirelessMultiPaxosPayload build(WirelessPaxosPhase phase, int proposalNumber, int slotStart,
                                                   List<LogEntry> entries, int minProposal,
                                                   Set<Integer> noProposalNodes) {
        List<LogEntry> normalized = normalize(entries, slotStart, Integer.MAX_VALUE);
        int slotCount = normalized.size();
        return new WirelessMultiPaxosPayload(phase, proposalNumber, slotStart, slotCount, normalized, minProposal,
                normalizeNoProposal(noProposalNodes));
    }

    public WirelessMultiPaxosPayload withEntries(List<LogEntry> updatedEntries, int slotCapacity) {
        int start = updatedEntries.isEmpty() ? slotStart : updatedEntries.get(0).slotIndex();
        List<LogEntry> normalized = normalize(updatedEntries, start, slotCapacity);
        return new WirelessMultiPaxosPayload(phase, proposalNumber, start, normalized.size(), normalized, minProposal,
                noProposalNodes);
    }

    public WirelessMultiPaxosPayload withMinProposal(int nextMinProposal) {
        return new WirelessMultiPaxosPayload(phase, proposalNumber, slotStart, slotCount, entries, nextMinProposal,
                noProposalNodes);
    }

    public WirelessMultiPaxosPayload withNoProposalNodes(Set<Integer> nodes) {
        return new WirelessMultiPaxosPayload(phase, proposalNumber, slotStart, slotCount, entries, minProposal,
                normalizeNoProposal(nodes));
    }

    public boolean isEmptyAttempt() {
        return proposalNumber < 0;
    }

    public static WirelessMultiPaxosPayload mergePayloads(WirelessMultiPaxosPayload left,
                                                          WirelessMultiPaxosPayload right, int slotCapacity) {
        if (left == null) {
            return right != null ? right : empty(slotCapacity);
        }
        if (right == null) {
            return left;
        }

        WirelessMultiPaxosPayload dominant = pickDominant(left, right);

        // Only merge entries for the slots covered by the dominant chunk.
        Map<Integer, LogEntry> mergedEntries = new HashMap<>();
        int chunkStart = dominant.slotStart();
        int chunkEnd = chunkStart + Math.max(1, dominant.slotCount());

        // Seed with dominant entries.
        for (LogEntry entry : dominant.entries()) {
            mergedEntries.put(entry.slotIndex(), entry);
        }
        // Bring in higher-accepted entries for the same slots from the other payload.
        WirelessMultiPaxosPayload secondary = (dominant == left) ? right : left;
        for (LogEntry entry : secondary.entries()) {
            if (entry.slotIndex() < chunkStart || entry.slotIndex() >= chunkEnd) {
                continue; // ignore other chunks
            }
            LogEntry current = mergedEntries.get(entry.slotIndex());
            if (current == null
                    || entry.acceptedProposal() > current.acceptedProposal()
                    || (entry.acceptedProposal() == current.acceptedProposal()
                            && current.acceptedValue() == null && entry.acceptedValue() != null)) {
                mergedEntries.put(entry.slotIndex(), entry);
            }
        }

        List<LogEntry> normalized = normalize(new ArrayList<>(mergedEntries.values()),
                dominant.slotStart(), slotCapacity);
        int slotCount = normalized.size();
        int min = Math.max(left.minProposal(), right.minProposal());
        Set<Integer> mergedNoProposal = mergeNoProposal(left.noProposalNodes(), right.noProposalNodes());
        return new WirelessMultiPaxosPayload(dominant.phase(), dominant.proposalNumber(),
                normalized.isEmpty() ? dominant.slotStart() : normalized.get(0).slotIndex(),
                slotCount,
                normalized,
                min,
                mergedNoProposal);
    }

    private static WirelessMultiPaxosPayload pickDominant(WirelessMultiPaxosPayload left,
                                                          WirelessMultiPaxosPayload right) {
        if (left.proposalNumber() != right.proposalNumber()) {
            return left.proposalNumber() > right.proposalNumber() ? left : right;
        }
        if (left.phase() != right.phase()) {
            return left.phase() == WirelessPaxosPhase.ACCEPT ? left : right;
        }
        if (left.slotStart() != right.slotStart()) {
            // Prefer the newer chunk (higher slotStart) when proposal/phase are the same.
            return left.slotStart() > right.slotStart() ? left : right;
        }
        return left;
    }

    private static List<LogEntry> normalize(List<LogEntry> source, int start, int slotCapacity) {
        List<LogEntry> copy = new ArrayList<>();
        for (LogEntry entry : source) {
            if (entry != null) {
                copy.add(entry);
            }
        }
        copy.sort(Comparator.comparingInt(LogEntry::slotIndex));
        if (copy.size() > slotCapacity) {
            copy = copy.subList(0, slotCapacity);
        }
        return List.copyOf(copy);
    }

    private static Set<Integer> normalizeNoProposal(Set<Integer> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new TreeSet<>(nodes));
    }

    private static Set<Integer> mergeNoProposal(Set<Integer> left, Set<Integer> right) {
        if ((left == null || left.isEmpty()) && (right == null || right.isEmpty())) {
            return Set.of();
        }
        TreeSet<Integer> merged = new TreeSet<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return Set.copyOf(merged);
    }

    public record LogEntry(int slotIndex, int acceptedProposal, Object acceptedValue, Object proposedValue) {
        public LogEntry {
            Objects.requireNonNull(Integer.valueOf(slotIndex), "slotIndex");
        }
    }
}
