package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.RoundRobinInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodApplication;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodSettings;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodStrategies;
import ir.ac.kntu.concurrenttransmission.blueflood.DefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.graph.CtNodeFactory;
import ir.ac.kntu.concurrenttransmission.graph.ReflectionCtNodeFactory;
import ir.ac.kntu.distributedsystems.fault.om.OmAction;
import ir.ac.kntu.distributedsystems.fault.om.ReplicatedWriteOralMessage;
import ir.ac.kntu.simulation.ScenarioRunner;
import ir.ac.kntu.simulation.SimulationScenario;

import java.nio.file.Path;

public class Main implements SimulationScenario<BlueFloodApplication> {

    private static final int BLUEFLOOD_REPEAT_SLOTS = 3;
    private static final int EXECUTION_ROUNDS = 5;
    private static final double LOSS_PROBABILITY = 0.0;

    public static void main(String[] args) {
        ScenarioRunner.run(new Main());
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
        netGraph.getNodes().forEach(node -> application
                .setListener(node, new ReplicatedWriteOralMessage(OmAction.Retreat, 1, OmAction.Attack)));
    }

    @Override
    public void onSimulationFinished(BlueFloodApplication application) {
        System.out.println(application.printTimeline());
    }
}
