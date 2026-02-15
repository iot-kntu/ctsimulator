package ir.ac.kntu.experiment;

import java.util.List;

public record ExperimentConfig(
        List<String> algorithms,
        List<Integer> nodeCounts,
        List<Double> lossRates,
        List<Double> failureRates,
        List<Integer> loads,
        List<Long> seeds,
        List<String> topologyTypes,
        List<TopologyConfig> topologies,
        Integer timeoutSlots,
        Integer slotDurationMs,
        Integer maxRecoveries,
        String resultsDir,
        Boolean exportYaml,
        FaultModelConfig faultModel
) {

    public record TopologyConfig(
            String name,
            int nodeCount,
            String graphPath
    ) {
    }

    public record FaultModelConfig(
            Double silentNodeRatio,
            Double faultyNodeRatio,
            Integer delayJitterSlots
    ) {
    }
}
