package ir.ac.kntu.metrics;

public record RunMetrics(
        String runId,
        String algorithm,
        String framework,
        int nNodes,
        String topology,
        double lossRate,
        double failureRate,
        int load,
        long seed,
        double decisionSuccess,
        double decisionLatencySlots,
        Double decisionLatencyMs,
        long totalMessages,
        long successfulMessages,
        long failedMessages,
        long failedCollision,
        long failedDrop,
        long failedSilent,
        long failedFaulty,
        long failedNotListening,
        Double p95Latency,
        Double p99Latency,
        Double throughput
) {
}
