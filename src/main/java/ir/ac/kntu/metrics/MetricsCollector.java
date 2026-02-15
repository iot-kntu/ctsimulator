package ir.ac.kntu.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class MetricsCollector {

    private final String runId;
    private final String algorithm;
    private final String framework;
    private final int nNodes;
    private final String topology;
    private final double lossRate;
    private final double failureRate;
    private final int load;
    private final long seed;
    private final Integer slotDurationMs;

    private final Map<Integer, NodeMetrics> nodeMetrics = new HashMap<>();
    private final Map<Integer, DecisionMetrics> decisions = new LinkedHashMap<>();

    private long totalMessages;
    private long successfulMessages;
    private long failedMessages;
    private long failedCollision;
    private long failedDrop;
    private long failedSilent;
    private long failedFaulty;
    private long failedNotListening;

    private long earliestStart = Long.MAX_VALUE;
    private long latestEnd = Long.MIN_VALUE;

    public MetricsCollector(String runId,
                            String algorithm,
                            String framework,
                            int nNodes,
                            String topology,
                            double lossRate,
                            double failureRate,
                            int load,
                            long seed,
                            Integer slotDurationMs) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.framework = Objects.requireNonNull(framework, "framework");
        this.nNodes = nNodes;
        this.topology = topology != null ? topology : "unknown";
        this.lossRate = lossRate;
        this.failureRate = failureRate;
        this.load = load;
        this.seed = seed;
        this.slotDurationMs = slotDurationMs;
    }

    public String runId() {
        return runId;
    }

    public void registerNode(int nodeId) {
        node(nodeId);
    }

    public void recordDecisionStart(int round, Integer initiatorId, long time) {
        DecisionMetrics decision = decisions.computeIfAbsent(round, key -> new DecisionMetrics(round, initiatorId));
        if (decision.initiatorId == null && initiatorId != null) {
            decision.initiatorId = initiatorId;
        }
        if (decision.startTime == null || time < decision.startTime) {
            decision.startTime = time;
        }
        if (time < earliestStart) {
            earliestStart = time;
        }
    }

    public void recordDecisionEnd(int round, int nodeId, long time, boolean committed) {
        DecisionMetrics decision = decisions.computeIfAbsent(round, key -> new DecisionMetrics(round, null));
        Long previous = decision.nodeDecisionTimes.putIfAbsent(nodeId, time);
        if (previous == null) {
            if (time > latestEnd) {
                latestEnd = time;
            }
            if (committed) {
                node(nodeId).commitCount++;
            }
        }
    }

    public void recordPhaseTime(int round, int nodeId, String phase, long time) {
        if (phase == null) {
            return;
        }
        DecisionMetrics decision = decisions.computeIfAbsent(round, key -> new DecisionMetrics(round, null));
        Map<String, Long> nodePhases = decision.phaseTimes.computeIfAbsent(nodeId, key -> new TreeMap<>());
        nodePhases.putIfAbsent(phase, time);
    }

    public void recordSendAttempt(int round, int senderId, int receiverId) {
        totalMessages++;
        NodeMetrics node = node(senderId);
        node.sent++;
        node.participatedRounds.add(round);
    }

    public void recordSendSuppressed(int round, int senderId, int receiverId, FailureReason reason) {
        totalMessages++;
        failedMessages++;
        incrementFailure(reason);
        NodeMetrics node = node(senderId);
        node.sent++;
        node.failed++;
        node.participatedRounds.add(round);
    }

    public void recordReceiveSuccess(int round, int receiverId) {
        successfulMessages++;
        NodeMetrics node = node(receiverId);
        node.received++;
        node.success++;
        node.participatedRounds.add(round);
    }

    public void recordReceiveFailure(int round, int receiverId, FailureReason reason) {
        failedMessages++;
        incrementFailure(reason);
        NodeMetrics node = node(receiverId);
        node.received++;
        node.failed++;
        node.participatedRounds.add(round);
    }

    private void incrementFailure(FailureReason reason) {
        if (reason == null) {
            return;
        }
        switch (reason) {
            case DROP -> failedDrop++;
            case COLLISION -> failedCollision++;
            case SILENT -> failedSilent++;
            case FAULTY -> failedFaulty++;
            case NOT_LISTENING -> failedNotListening++;
        }
    }

    public RunMetrics toRunMetrics() {
        List<Long> latencies = new ArrayList<>();
        int totalDecisions = decisions.size();
        int successfulDecisions = 0;

        for (DecisionMetrics decision : decisions.values()) {
            if (decision.startTime == null) {
                continue;
            }
            if (decision.nodeDecisionTimes.size() == nNodes) {
                long maxEnd = decision.nodeDecisionTimes.values().stream()
                        .max(Long::compareTo)
                        .orElse(decision.startTime);
                latencies.add(maxEnd - decision.startTime);
                successfulDecisions++;
            }
        }

        double decisionSuccess = totalDecisions == 0 ? 0.0 : (double) successfulDecisions / totalDecisions;
        double decisionLatencySlots = average(latencies);
        Double decisionLatencyMs = slotDurationMs == null ? null : decisionLatencySlots * slotDurationMs;

        Double p95 = percentile(latencies, 0.95);
        Double p99 = percentile(latencies, 0.99);

        Double throughput = null;
        if (successfulDecisions > 0 && latestEnd >= earliestStart) {
            long elapsedSlots = Math.max(1, latestEnd - earliestStart + 1);
            throughput = successfulDecisions / (double) elapsedSlots;
        }

        return new RunMetrics(
                runId,
                algorithm,
                framework,
                nNodes,
                topology,
                lossRate,
                failureRate,
                load,
                seed,
                decisionSuccess,
                decisionLatencySlots,
                decisionLatencyMs,
                totalMessages,
                successfulMessages,
                failedMessages,
                failedCollision,
                failedDrop,
                failedSilent,
                failedFaulty,
                failedNotListening,
                p95,
                p99,
                throughput
        );
    }

    public RunDetail toRunDetail() {
        Map<Integer, RunDetail.NodeDetail> nodes = new TreeMap<>();
        for (Map.Entry<Integer, NodeMetrics> entry : nodeMetrics.entrySet()) {
            NodeMetrics metrics = entry.getValue();
            nodes.put(entry.getKey(), new RunDetail.NodeDetail(
                    metrics.sent,
                    metrics.received,
                    metrics.success,
                    metrics.failed,
                    metrics.participatedRounds.size(),
                    metrics.commitCount
            ));
        }

        Map<Integer, RunDetail.DecisionDetail> decisionDetails = new TreeMap<>();
        for (DecisionMetrics decision : decisions.values()) {
            Map<Integer, Long> nodeTimes = new TreeMap<>(decision.nodeDecisionTimes);
            Map<Integer, Map<String, Long>> phaseTimes = new TreeMap<>();
            decision.phaseTimes.forEach((nodeId, phases) -> phaseTimes.put(nodeId, new TreeMap<>(phases)));

            decisionDetails.put(decision.round, new RunDetail.DecisionDetail(
                    decision.round,
                    decision.initiatorId,
                    decision.startTime,
                    nodeTimes,
                    phaseTimes
            ));
        }

        return new RunDetail(runId, algorithm, framework, nNodes, topology, load, seed, nodes, decisionDetails);
    }

    private NodeMetrics node(int nodeId) {
        return nodeMetrics.computeIfAbsent(nodeId, key -> new NodeMetrics());
    }

    private static double average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        long total = 0;
        for (Long value : values) {
            total += value;
        }
        return total / (double) values.size();
    }

    private static Double percentile(List<Long> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.min(Math.max(index, 0), sorted.size() - 1);
        return sorted.get(index).doubleValue();
    }

    private static final class NodeMetrics {
        long sent;
        long received;
        long success;
        long failed;
        int commitCount;
        Set<Integer> participatedRounds = new TreeSet<>();
    }

    private static final class DecisionMetrics {
        final int round;
        Integer initiatorId;
        Long startTime;
        Map<Integer, Long> nodeDecisionTimes = new HashMap<>();
        Map<Integer, Map<String, Long>> phaseTimes = new HashMap<>();

        DecisionMetrics(int round, Integer initiatorId) {
            this.round = round;
            this.initiatorId = initiatorId;
        }
    }
}
