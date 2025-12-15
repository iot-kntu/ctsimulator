package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.RoundRobinInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodApplication;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodSettings;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodStrategies;
import ir.ac.kntu.concurrenttransmission.blueflood.DefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.graph.CtNodeFactory;
import ir.ac.kntu.concurrenttransmission.graph.ReflectionCtNodeFactory;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;
import ir.ac.kntu.distributedsystems.bf.twopc.BlueFloodTwoPhaseCommit;
import ir.ac.kntu.simulation.ScenarioRunner;
import ir.ac.kntu.simulation.SimulationScenario;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

public class BlueFloodTwoPhaseCommitSenario implements SimulationScenario<BlueFloodApplication> {

    private static final int BLUEFLOOD_REPEAT_SLOTS = 3;
    private static final int EXECUTION_ROUNDS = 8;
    private static final double LOSS_PROBABILITY = 0.0;
    private static final Logger logger = Logger.getLogger(BlueFloodTwoPhaseCommitSenario.class.getSimpleName());

    private NetGraph netGraph;

    public static void main(String[] args) {
        ScenarioRunner.run(new BlueFloodTwoPhaseCommitSenario());
    }

    @Override
    public Path graphPath() {
        return Path.of("sample.graph");
    }

    @Override
    public CtNodeFactory nodeFactory() {
        return new ReflectionCtNodeFactory("ir.ac.kntu.concurrenttransmission.blueflood.nodes");
    }

    @Override
    public BlueFloodApplication createApplication(NetGraph netGraph) {
        BlueFloodSettings settings = new BlueFloodSettings(
                LOSS_PROBABILITY,
                BlueFloodApplication.DEFAULT_INTERFERENCE_PROB,
                EXECUTION_ROUNDS);

        BlueFloodStrategies strategies = new BlueFloodStrategies(
                new RoundRobinInitiatorStrategy(netGraph.getNodeCount()),
                new DefaultTransmissionPolicy(BLUEFLOOD_REPEAT_SLOTS, netGraph));

        return new BlueFloodApplication(settings, strategies);
    }

    @Override
    public void configure(NetGraph netGraph, BlueFloodApplication application) {
        this.netGraph = netGraph;
        application.configureScenarioMetadata("BlueFlood 2PC", "ctsimulator", "Two phase commit over BlueFlood");
        netGraph.getNodes().forEach(node -> {
            Queue<Object> proposals = new LinkedList<>();
            proposals.add("proposal-" + node.getId());

            Queue<VoteValue> votes = new LinkedList<>();
            for (int i = 0; i < netGraph.getNodeCount(); i++) {
                votes.add(VoteValue.YES);
            }

            application.setListener(node, new BlueFloodTwoPhaseCommit(proposals, votes));
        });
    }

    @Override
    public void onSimulationFinished(BlueFloodApplication application) {
        System.out.println(application.printTimeline());
        application.exportScenarioReport(netGraph)
                .ifPresent(path -> logger.info("BlueFlood YAML exported to " + path.toAbsolutePath()));
    }
}
