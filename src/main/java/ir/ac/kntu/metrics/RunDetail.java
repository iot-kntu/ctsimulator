package ir.ac.kntu.metrics;

import java.util.Map;

public record RunDetail(
        String runId,
        String algorithm,
        String framework,
        int nNodes,
        String topology,
        int load,
        long seed,
        Map<Integer, NodeDetail> nodes,
        Map<Integer, DecisionDetail> decisions
) {

    public record NodeDetail(
            long sent,
            long received,
            long success,
            long failed,
            int participatedRounds,
            int commitCount
    ) {
    }

    public record DecisionDetail(
            int round,
            Integer initiatorId,
            Long startTime,
            Map<Integer, Long> decisionTimes,
            Map<Integer, Map<String, Long>> phaseTimes
    ) {
    }
}
