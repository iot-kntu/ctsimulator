package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.RoundRobinInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosSettings;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStrategies;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.graph.CtNodeFactory;
import ir.ac.kntu.concurrenttransmission.graph.ReflectionCtNodeFactory;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxos;
import ir.ac.kntu.distributedsystems.paxos.wpaxos.WirelessPaxosTransmissionPolicy;
import ir.ac.kntu.simulation.ScenarioRunner;
import ir.ac.kntu.simulation.SimulationScenario;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Runs the Wireless Paxos scenario on top of the Chaos simulator.
 */
public class WirelessPaxosScenario implements SimulationScenario<ChaosApplication> {

    private static final int EXECUTION_ROUNDS = 1;
    private static final int FLOOD_REPEAT_SLOTS = 1;
    private static final int FINAL_FLOOD_REPEAT_SLOTS = 3;

    public static void main(String[] args) {
        ScenarioRunner.run(new WirelessPaxosScenario());
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
        ChaosTransmissionPolicy transmissionPolicy = new WirelessPaxosTransmissionPolicy(
                FLOOD_REPEAT_SLOTS,
                FINAL_FLOOD_REPEAT_SLOTS,
                netGraph);
        ChaosStrategies strategies = new ChaosStrategies(
                new RoundRobinInitiatorStrategy(netGraph.getNodeCount()),
                transmissionPolicy);
        return new ChaosApplication(settings, strategies, netGraph, transmissionPolicy.getInitialState());
    }

    @Override
    public void configure(NetGraph netGraph, ChaosApplication application) {
        netGraph.getNodes().forEach(node -> {
            Queue<Object> proposedValues = new LinkedList<>();
            proposedValues.add("UPDATE_FROM_NODE_" + node.getId());
            proposedValues.add("ROLLBACK_FROM_NODE_" + node.getId());
            application.setListener(node, new WirelessPaxos(proposedValues));
        });
    }

    @Override
    public void onSimulationFinished(ChaosApplication application) {
        System.out.println(application.getStateLogger().printTimeline());
    }
}
