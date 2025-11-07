package ir.ac.kntu.simulation;

import ir.ac.kntu.concurrenttransmission.ConcurrentTransmissionApplication;
import ir.ac.kntu.concurrenttransmission.CtSimulator;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class ScenarioRunner {

    private ScenarioRunner() {
    }

    public static <A extends ConcurrentTransmissionApplication> void run(SimulationScenario<A> scenario) {
        Objects.requireNonNull(scenario);
        try {
            LoggingConfigurator.configure();

            Path graphPath = scenario.graphPath();
            validateGraphPath(graphPath);

            NetGraph netGraph = NetGraph.loadFrom(graphPath.toString(), scenario.nodeFactory());
            if (netGraph.isEmpty()) {
                throw new IllegalArgumentException("Invalid graph file format, it is not loaded");
            }

            final int graphDiameter = netGraph.getDiameter();
            System.out.println("graphDiameter = " + graphDiameter);
            System.out.println("================================");

            A application = scenario.createApplication(netGraph);
            if (application instanceof ChaosApplication chaosApplication) {
                chaosApplication.configureScenarioMetadata(
                        scenario.getClass().getSimpleName(),
                        System.getProperty("user.name", "Unknown Author"),
                        "Auto-generated report for " + scenario.getClass().getSimpleName()
                );
            }
            scenario.configure(netGraph, application);

            CtSimulator simulator = CtSimulator.createInstance(netGraph, application);
            simulator.start();

            Optional<Path> reportPath = Optional.empty();
            if (application instanceof ChaosApplication chaosApplication) {
                reportPath = chaosApplication.exportScenarioReport(netGraph);
            }

            scenario.onSimulationFinished(application);

            reportPath.ifPresent(path -> System.out.println("Scenario report saved to " + path.toAbsolutePath()));

        } catch (Exception e) {
            System.err.println("High level error occurred: ");
            e.printStackTrace();
        }
    }

    private static void validateGraphPath(Path graphPath) {
        if (graphPath == null) {
            throw new IllegalArgumentException("Graph path is not specified");
        }
        if (!Files.exists(graphPath)) {
            throw new IllegalArgumentException("Graph file not found: " + graphPath.toAbsolutePath());
        }
    }
}
