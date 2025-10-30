package ir.ac.kntu.simulation;

import ir.ac.kntu.concurrenttransmission.ConcurrentTransmissionApplication;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.graph.CtNodeFactory;

import java.nio.file.Path;

public interface SimulationScenario<A extends ConcurrentTransmissionApplication> {

    Path graphPath();

    CtNodeFactory nodeFactory();

    A createApplication(NetGraph netGraph);

    default void configure(NetGraph netGraph, A application) {
        // default no-op
    }

    default void onSimulationFinished(A application) {
        // default no-op
    }
}
