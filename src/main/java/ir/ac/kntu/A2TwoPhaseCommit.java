package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.CtSimulator;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.OneInitiatorInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosSettings;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStrategies;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosTransmissionPolicy;
import ir.ac.kntu.distributedsystems.a2.twopc.TwoPcTransmissionPolicy;
import ir.ac.kntu.distributedsystems.a2.twopc.TwoPhaseCommit;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.LogManager;

public class A2TwoPhaseCommit {
    public static void main(String[] args) {
        try {
            startLogger(); // Assuming you have a utility class for this

            final NetGraph netGraph = NetGraph.loadFrom("sample.graph");
            System.out.println("Graph diameter = " + netGraph.getDiameter());
            System.out.println("================================");

            if (netGraph.isEmpty())
                throw new IllegalArgumentException("Invalid graph file format.");

            final int executionRounds = 1;
            final int floodRepeatSlots = 1;
            final int finalFloodRepeatSlots = 3;

            ChaosSettings settings = new ChaosSettings(0.0, executionRounds);

            ChaosTransmissionPolicy transmissionPolicy = new TwoPcTransmissionPolicy(floodRepeatSlots,
                    finalFloodRepeatSlots, netGraph);

            final int coordinatorId = 0;
            ChaosStrategies strategies = new ChaosStrategies(
                    new OneInitiatorInitiatorStrategy(coordinatorId),
                    transmissionPolicy);

            ChaosApplication chaosApplication = new ChaosApplication(settings, strategies, netGraph,
                    transmissionPolicy.getInitialState());

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

                chaosApplication.setListener(node, new TwoPhaseCommit(proposals, votes));
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
