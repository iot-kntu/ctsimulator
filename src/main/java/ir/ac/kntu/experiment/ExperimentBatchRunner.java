package ir.ac.kntu.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import ir.ac.kntu.concurrenttransmission.CtSimulator;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.OneInitiatorInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.RoundRobinInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosSettings;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStrategies;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodApplication;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodSettings;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodStrategies;
import ir.ac.kntu.concurrenttransmission.blueflood.DefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.graph.CtNodeFactory;
import ir.ac.kntu.concurrenttransmission.graph.ReflectionCtNodeFactory;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePcTransmissionPolicy;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommit;
import ir.ac.kntu.distributedsystems.a2.twopc.TwoPcTransmissionPolicy;
import ir.ac.kntu.distributedsystems.a2.twopc.TwoPhaseCommit;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;
import ir.ac.kntu.distributedsystems.bf.paxos.BlueFloodPaxos;
import ir.ac.kntu.distributedsystems.bf.threepc.BlueFloodThreePhaseCommit;
import ir.ac.kntu.distributedsystems.bf.tom.BlueFloodTotalOrderMulticast;
import ir.ac.kntu.distributedsystems.bf.twopc.BlueFloodTwoPhaseCommit;
import ir.ac.kntu.distributedsystems.paxos.wmultipaxos.WirelessMultiPaxos;
import ir.ac.kntu.distributedsystems.paxos.wmultipaxos.WirelessMultiPaxosTransmissionPolicy;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxos;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosTransmissionPolicy;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsCsvWriter;
import ir.ac.kntu.metrics.MetricsDetailWriter;
import ir.ac.kntu.metrics.RunMetrics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public final class ExperimentBatchRunner {

    private static final int CHAOS_FLOOD_REPEAT = 1;
    private static final int CHAOS_FINAL_FLOOD_REPEAT = 1;
    private static final int BLUEFLOOD_REPEAT = 3;

    private ExperimentBatchRunner() {
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Usage: ExperimentBatchRunner <config.yaml|json>");
            return;
        }
        Path configPath = Path.of(args[0]);
        try {
            ExperimentConfig config = loadConfig(configPath);
            runBatch(config);
        } catch (Exception e) {
            System.err.println("Failed to run batch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static ExperimentConfig loadConfig(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();
        ObjectMapper mapper = fileName.endsWith(".yaml") || fileName.endsWith(".yml")
                ? new ObjectMapper(new YAMLFactory())
                : new ObjectMapper();
        return mapper.readValue(path.toFile(), ExperimentConfig.class);
    }

    private static void runBatch(ExperimentConfig config) throws IOException {
        List<AlgorithmType> algorithms = parseAlgorithms(config.algorithms());
        List<Integer> nodeCounts = requireNonEmpty(config.nodeCounts(), "nodeCounts");
        List<Double> lossRates = requireNonEmpty(config.lossRates(), "lossRates");
        List<Integer> loads = requireNonEmpty(config.loads(), "loads");
        List<Long> seeds = requireNonEmpty(config.seeds(), "seeds");
        List<String> topologyTypes = requireNonEmpty(config.topologyTypes(), "topologyTypes");

        Map<String, Map<Integer, Path>> topologyMap = buildTopologyMap(config.topologies());

        Path resultsDir = Path.of(config.resultsDir() == null ? "results" : config.resultsDir());
        Path metricsCsv = resultsDir.resolve("metrics.csv");
        Path failedCsv = resultsDir.resolve("failed_runs.csv");
        Files.createDirectories(resultsDir);

        int timeoutSlots = config.timeoutSlots() == null ? Integer.MAX_VALUE : config.timeoutSlots();
        Integer slotDurationMs = config.slotDurationMs();
        boolean exportYaml = config.exportYaml() == null || config.exportYaml();

        Double fadingStdDevDb = config.fadingStdDevDb();

        for (AlgorithmType algorithm : algorithms) {
            for (int nodeCount : nodeCounts) {
                for (double lossRate : lossRates) {
                    for (int load : loads) {
                        for (long seed : seeds) {
                            for (String topology : topologyTypes) {
                                String runId = UUID.randomUUID().toString();
                                Path graphPath = resolveGraphPath(topologyMap, topology, nodeCount);
                                try {
                                    int maxRecoveries = config.maxRecoveries() == null ? 1 : config.maxRecoveries();
                                    RunMetrics metrics = runSingle(algorithm, graphPath, topology, nodeCount,
                                            lossRate, load, seed, timeoutSlots, slotDurationMs, resultsDir, runId,
                                            exportYaml, maxRecoveries, fadingStdDevDb);
                                    MetricsCsvWriter.append(metricsCsv, metrics);
                                } catch (Exception e) {
                                    writeFailedRun(failedCsv, runId, algorithm, topology, nodeCount, lossRate,
                                            load, seed, e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static RunMetrics runSingle(AlgorithmType algorithm,
            Path graphPath,
            String topology,
            int nodeCount,
            double lossRate,
            int load,
            long seed,
            int timeoutSlots,
            Integer slotDurationMs,
            Path resultsDir,
            String runId,
            boolean exportYaml,
            int maxRecoveries,
            Double fadingStdDevDb) throws Exception {
        boolean chaos = isChaos(algorithm);
        CtNodeFactory nodeFactory = chaos
                ? new ReflectionCtNodeFactory("ir.ac.kntu.concurrenttransmission.chaos.nodes")
                : new ReflectionCtNodeFactory("ir.ac.kntu.concurrenttransmission.blueflood.nodes");

        NetGraph netGraph = NetGraph.loadFrom(graphPath.toString(), nodeFactory);

        MetricsCollector metricsCollector = new MetricsCollector(
                runId,
                algorithm.name(),
                chaos ? "CHAOS" : "BLUEFLOOD",
                netGraph.getNodeCount(),
                topology,
                lossRate,
                load,
                seed,
                slotDurationMs);
        netGraph.getNodes().forEach(node -> metricsCollector.registerNode(node.getId()));

        String author = System.getProperty("user.name", "Unknown Author");
        String scenarioName = algorithm.name() + "-" + runId;
        String scenarioDescription = "batch run " + runId + " topology=" + topology + " nodes="
                + netGraph.getNodeCount() + " loss=" + lossRate + " load=" + load
                + " seed=" + seed;

        if (chaos) {
            ChaosApplication application = buildChaosApplication(algorithm, netGraph, lossRate, load);
            application.setMetricsCollector(metricsCollector);
            application.setRandomSeed(seed);
            application.setFadingStandardDeviationDb(fadingStdDevDb);
            application.configureScenarioMetadata(scenarioName, author, scenarioDescription);
            configureChaosListeners(algorithm, netGraph, application, load, maxRecoveries);

            CtSimulator simulator = CtSimulator.createInstance(netGraph, application);
            boolean completed = simulator.start(timeoutSlots);
            System.out.println(completed);
            if (!completed) {
                throw new RuntimeException("timeout");
            }
            if (exportYaml) {
                application.exportScenarioReport(netGraph);
            }
        } else {
            int blueFloodRounds = computeBlueFloodRounds(algorithm, load, netGraph.getNodeCount());
            BlueFloodApplication application = buildBlueFloodApplication(netGraph, lossRate, blueFloodRounds);
            application.setMetricsCollector(metricsCollector);
            application.setRandomSeed(seed);
            application.configureScenarioMetadata(scenarioName, author, scenarioDescription);
            configureBlueFloodListeners(algorithm, netGraph, application, load);

            CtSimulator simulator = CtSimulator.createInstance(netGraph, application);
            boolean completed = simulator.start(timeoutSlots);
            if (!completed) {
                throw new RuntimeException("timeout");
            }
            if (exportYaml) {
                application.exportScenarioReport(netGraph);
            }
        }

        RunMetrics metrics = metricsCollector.toRunMetrics();
        MetricsDetailWriter.write(resultsDir.resolve("run_" + runId + "_detail.json"),
                metricsCollector.toRunDetail());
        return metrics;
    }

    private static ChaosApplication buildChaosApplication(AlgorithmType algorithm, NetGraph netGraph,
            double lossRate, int load) {
        int rounds = Math.max(1, load);
        ChaosSettings settings = new ChaosSettings(lossRate, rounds);

        ChaosTransmissionPolicy policy = switch (algorithm) {
            case WIRELESS_PAXOS -> new WirelessPaxosTransmissionPolicy(
                    CHAOS_FLOOD_REPEAT, CHAOS_FINAL_FLOOD_REPEAT, netGraph);
            case WIRELESS_MULTIPAXOS -> new WirelessMultiPaxosTransmissionPolicy(
                    CHAOS_FLOOD_REPEAT, CHAOS_FINAL_FLOOD_REPEAT, netGraph);
            case A2_WIRELESS_MULTIPAXOS -> new ir.ac.kntu.distributedsystems.a2.wmultipaxos.WirelessMultiPaxosTransmissionPolicy(
                    CHAOS_FLOOD_REPEAT, CHAOS_FINAL_FLOOD_REPEAT, netGraph);
            case CHAOS_2PC -> new TwoPcTransmissionPolicy(
                    CHAOS_FLOOD_REPEAT, CHAOS_FINAL_FLOOD_REPEAT, netGraph);
            case CHAOS_3PC -> new ThreePcTransmissionPolicy(
                    CHAOS_FLOOD_REPEAT, CHAOS_FINAL_FLOOD_REPEAT, netGraph);
            default -> throw new IllegalArgumentException("Not a Chaos algorithm: " + algorithm);
        };

        ChaosStrategies strategies = new ChaosStrategies(
                load == 1
                        ? new OneInitiatorInitiatorStrategy(0)
                        : new RoundRobinInitiatorStrategy(netGraph.getNodeCount()),
                policy);

        return new ChaosApplication(settings, strategies, netGraph, policy.getInitialState());
    }

    private static BlueFloodApplication buildBlueFloodApplication(NetGraph netGraph, double lossRate, int rounds) {
        int safeRounds = Math.max(1, rounds);
        BlueFloodSettings settings = new BlueFloodSettings(lossRate, lossRate, safeRounds);
        BlueFloodStrategies strategies = new BlueFloodStrategies(
                new RoundRobinInitiatorStrategy(netGraph.getNodeCount()),
                new DefaultTransmissionPolicy(BLUEFLOOD_REPEAT, netGraph));
        return new BlueFloodApplication(settings, strategies);
    }

    private static int computeBlueFloodRounds(AlgorithmType algorithm, int load, int nodeCount) {
        // int nodes = Math.max(1, nodeCount);
        // int base = Math.max(1, load) * nodes;
        // if (algorithm == AlgorithmType.BLUEFLOOD_3PC) {
        //     return base + (2 * nodes);
        // }
        // return base;
        return Math.max(1, load);
    }

    private static void configureChaosListeners(AlgorithmType algorithm, NetGraph netGraph,
            ChaosApplication application, int load, int maxRecoveries) {
        switch (algorithm) {
            case WIRELESS_PAXOS -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("VAL_" + node.getId() + "_" + i);
                }
                int safeRecoveries = Math.max(0, maxRecoveries);
                application.setListener(node, new WirelessPaxos(proposals, safeRecoveries));
            });
            case WIRELESS_MULTIPAXOS -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("CMD_" + node.getId() + "_" + i);
                }
                application.setListener(node, new WirelessMultiPaxos(proposals));
            });
            case A2_WIRELESS_MULTIPAXOS -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("CMD_" + node.getId() + "_" + i);
                }
                int safeRecoveries = Math.max(0, maxRecoveries);
                application.setListener(node,
                        new ir.ac.kntu.distributedsystems.a2.wmultipaxos.WirelessMultiPaxos(
                                proposals, safeRecoveries));
            });
            case CHAOS_2PC -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("PROPOSAL_" + i);
                }
                Queue<VoteValue> votes = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load) * netGraph.getNodeCount(); i++) {
                    votes.add(VoteValue.YES);
                }
                application.setListener(node, new TwoPhaseCommit(proposals, votes));
            });
            case CHAOS_3PC -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("PROPOSAL_" + i);
                }
                Queue<VoteValue> votes = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load) * netGraph.getNodeCount(); i++) {
                    votes.add(VoteValue.YES);
                }
                application.setListener(node, new ThreePhaseCommit(proposals, votes));
            });
            default -> throw new IllegalArgumentException("Unsupported Chaos algorithm: " + algorithm);
        }
    }

    private static void configureBlueFloodListeners(AlgorithmType algorithm, NetGraph netGraph,
            BlueFloodApplication application, int load) {
        switch (algorithm) {
            case BLUEFLOOD_PAXOS -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("proposal-" + node.getId() + "-" + i);
                }
                Queue<VoteValue> votes = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load) * netGraph.getNodeCount(); i++) {
                    votes.add(VoteValue.YES);
                }
                application.setListener(node, new BlueFloodPaxos(proposals, votes));
            });
            case BLUEFLOOD_2PC -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("proposal-" + node.getId() + "-" + i);
                }
                Queue<VoteValue> votes = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load) * netGraph.getNodeCount(); i++) {
                    votes.add(VoteValue.YES);
                }
                application.setListener(node, new BlueFloodTwoPhaseCommit(proposals, votes));
            });
            case BLUEFLOOD_3PC -> netGraph.getNodes().forEach(node -> {
                Queue<Object> proposals = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    proposals.add("proposal-" + node.getId() + "-" + i);
                }
                Queue<VoteValue> votes = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load) * netGraph.getNodeCount(); i++) {
                    votes.add(VoteValue.YES);
                }
                application.setListener(node, new BlueFloodThreePhaseCommit(proposals, votes));
            });
            case BLUEFLOOD_TOM -> netGraph.getNodes().forEach(node -> {
                Queue<Object> outbound = new LinkedList<>();
                for (int i = 0; i < Math.max(1, load); i++) {
                    outbound.add("msg-" + node.getId() + "-" + i);
                }
                application.setListener(node, new BlueFloodTotalOrderMulticast(outbound));
            });
            default -> throw new IllegalArgumentException("Unsupported BlueFlood algorithm: " + algorithm);
        }
    }

    private static Path resolveGraphPath(Map<String, Map<Integer, Path>> topologyMap,
            String topology,
            int nodeCount) {
        Map<Integer, Path> byCount = topologyMap.get(topology);
        if (byCount == null || !byCount.containsKey(nodeCount)) {
            throw new IllegalArgumentException("No graph defined for topology=" + topology + " nodeCount="
                    + nodeCount);
        }
        return byCount.get(nodeCount);
    }

    private static Map<String, Map<Integer, Path>> buildTopologyMap(List<ExperimentConfig.TopologyConfig> configs) {
        Map<String, Map<Integer, Path>> map = new HashMap<>();
        if (configs == null) {
            return map;
        }
        for (ExperimentConfig.TopologyConfig config : configs) {
            map.computeIfAbsent(config.name(), key -> new HashMap<>())
                    .put(config.nodeCount(), Path.of(config.graphPath()));
        }
        return map;
    }

    private static List<AlgorithmType> parseAlgorithms(List<String> algorithms) {
        List<String> values = requireNonEmpty(algorithms, "algorithms");
        List<AlgorithmType> result = new ArrayList<>();
        for (String value : values) {
            result.add(AlgorithmType.fromString(value));
        }
        return result;
    }

    private static <T> List<T> requireNonEmpty(List<T> list, String name) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("Config missing " + name);
        }
        return list;
    }

    private static boolean isChaos(AlgorithmType algorithm) {
        return algorithm == AlgorithmType.WIRELESS_PAXOS
                || algorithm == AlgorithmType.WIRELESS_MULTIPAXOS
                || algorithm == AlgorithmType.A2_WIRELESS_MULTIPAXOS
                || algorithm == AlgorithmType.CHAOS_2PC
                || algorithm == AlgorithmType.CHAOS_3PC;
    }

    private static void writeFailedRun(Path path,
            String runId,
            AlgorithmType algorithm,
            String topology,
            int nodeCount,
            double lossRate,
            int load,
            long seed,
            String error) throws IOException {
        Files.createDirectories(path.getParent());
        boolean writeHeader = !Files.exists(path) || Files.size(path) == 0;

        try (BufferedWriter writer = Files.newBufferedWriter(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writer.write(String.join(",",
                        List.of("runId", "algorithm", "topology", "nodeCount", "lossRate", "load",
                                "seed", "error")));
                writer.newLine();
            }
            writer.write(String.join(",",
                    runId,
                    algorithm.name(),
                    topology,
                    String.valueOf(nodeCount),
                    String.valueOf(lossRate),
                    String.valueOf(load),
                    String.valueOf(seed),
                    escape(error)));
            writer.newLine();
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
