package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.CtSimulator;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.RoundRobinInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.chaos.*;
import ir.ac.kntu.distributedsystems.a2.collect.Collect;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.LogManager;

public class A2Collect {
    public static void main(String[] args) {
        try {
            startLogger();

            final NetGraph netGraph = NetGraph.loadFrom("sample.graph");
            System.out.println("Graph diameter = " + netGraph.getDiameter());
            System.out.println("================================");

            if (netGraph.isEmpty())
                throw new IllegalArgumentException("Invalid graph file format.");

            final int executionRounds = 2;
            final int floodRepeatSlots = 1;
            final int finalFloodRepeatSlots = 3;

            ChaosSettings settings = new ChaosSettings(0.0, executionRounds);

            ChaosTransmissionPolicy transmissionPolicy = new ChaosDefaultTransmissionPolicy(floodRepeatSlots,
                    finalFloodRepeatSlots, netGraph);

            ChaosStrategies strategies = new ChaosStrategies(
                    new RoundRobinInitiatorStrategy(netGraph.getNodeCount()),
                    transmissionPolicy);

            ChaosApplication chaosApplication = new ChaosApplication(settings, strategies, netGraph,
                    transmissionPolicy.getInitialState());

            netGraph.getNodes().forEach(node -> {
                Queue<Object> dataQueue = new LinkedList<>();
                dataQueue.add(node.getId() * node.getId());
                dataQueue.add(node.getId() * 10);

                chaosApplication.setListener(node, new Collect(dataQueue));
            });

            CtSimulator simulator = CtSimulator.createInstance(netGraph, chaosApplication);
            simulator.start();

            System.out.println(chaosApplication.getStateLogger().printTimeline());

        } catch (Exception e) {
            System.err.println("High level error occurred: ");
            e.printStackTrace();
        }
    }

    private static void startLogger() throws IOException {
        InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream("logging.properties");

        if (inputStream == null) {
            System.err.println("Cannot find logging config file, is package corrupted??");
            System.exit(2);
        }

        File logDir = new File("logs").getAbsoluteFile();
        if (!logDir.exists())
            logDir.mkdir();

        LogManager.getLogManager().readConfiguration(inputStream);
    }
}
