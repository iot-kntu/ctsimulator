package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.OneInitiatorInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosSettings;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStrategies;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.graph.CtNodeFactory;
import ir.ac.kntu.concurrenttransmission.graph.ReflectionCtNodeFactory;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePcTransmissionPolicy;
import ir.ac.kntu.distributedsystems.a2.threepc.ThreePhaseCommit;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;
import ir.ac.kntu.simulation.ScenarioRunner;
import ir.ac.kntu.simulation.SimulationScenario;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;

public class A2ThreePhaseCommit implements SimulationScenario<ChaosApplication> {

    private static final int EXECUTION_ROUNDS = 1;
    private static final int FLOOD_REPEAT_SLOTS = 1;
    private static final int FINAL_FLOOD_REPEAT_SLOTS = 3;
    private static final int COORDINATOR_ID = 0;

    public static void main(String[] args) {
        ScenarioRunner.run(new A2ThreePhaseCommit());
    }

    @Override
    public Path graphPath() {
        return Path.of("sample.graph");
    }

    @Override
    public CtNodeFactory nodeFactory() {
        return new ReflectionCtNodeFactory("ir.ac.kntu.concurrenttransmission.chaos.nodes");
    }

    @Override
    public ChaosApplication createApplication(NetGraph netGraph) {
        ChaosSettings settings = new ChaosSettings(0.0, EXECUTION_ROUNDS);

        ChaosTransmissionPolicy transmissionPolicy = new ThreePcTransmissionPolicy(
                FLOOD_REPEAT_SLOTS,
                FINAL_FLOOD_REPEAT_SLOTS,
                netGraph);

        ChaosStrategies strategies = new ChaosStrategies(
                new OneInitiatorInitiatorStrategy(COORDINATOR_ID),
                transmissionPolicy);

        return new ChaosApplication(settings, strategies, netGraph, transmissionPolicy.getInitialState());
    }

    @Override
    public void configure(NetGraph netGraph, ChaosApplication application) {
        final String proposal = "UPDATE_FIRMWARE_V2.1";

        netGraph.getNodes().forEach(node -> {
            Queue<Object> proposals = new LinkedList<>();
            proposals.add(proposal);

            Queue<VoteValue> votes = new LinkedList<>();
            if (node.getId() == 1 || node.getId() == 2) {
                votes.add(VoteValue.YES);
            } else if (node.getId() == 3) {
                votes.add(VoteValue.NO);
            }

            application.setListener(node, new ThreePhaseCommit(proposals, votes));
        });
    }

    @Override
    public void onSimulationFinished(ChaosApplication application) {
        System.out.println(application.getStateLogger().printTimeline());
    }
}
