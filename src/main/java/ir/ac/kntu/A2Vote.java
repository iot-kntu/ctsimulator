package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.CtSimulator;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.RoundRobinInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosApplication;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosDefaultTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosSettings;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStrategies;
import ir.ac.kntu.distributedsystems.a2.aggregation.Aggregation;
import ir.ac.kntu.distributedsystems.a2.vote.Vote;
import ir.ac.kntu.distributedsystems.a2.vote.VoteValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;


public class A2Vote {

    public static void main(String[] args) {

        try {
            startLogger();

            final NetGraph netGraph = NetGraph.loadFrom("sample.graph");
            final int graphDiameter = netGraph.getDiameter();
            System.out.println("graphDiameter = " + graphDiameter);
            System.out.println("================================");

            //noinspection
            if (netGraph.isEmpty())
                throw new IllegalArgumentException("Invalid graph file format, it is not loaded");

            final double lossProbability = 0.0;
            final int executionRounds = 4;
            final int floodRepeatSlots = 1;
            final int finalFloodRepeatSlots = 3;

            ChaosSettings settings = new ChaosSettings(
                    lossProbability,
                    executionRounds
            );

            ChaosStrategies strategies = new ChaosStrategies(
                    new RoundRobinInitiatorStrategy(netGraph.getNodeCount()),
                    new ChaosDefaultTransmissionPolicy(floodRepeatSlots, finalFloodRepeatSlots, netGraph)
            );


            ChaosApplication chaosApplication = new ChaosApplication(settings, strategies, netGraph);


            netGraph.getNodes().forEach(node -> {
                Queue<VoteValue> votes = new LinkedList<>();
                votes.add(VoteValue.YES);
                votes.add(VoteValue.YES);

                Queue<Integer> proposals = new LinkedList<>();
                proposals.add(node.getId());

                chaosApplication
                        .setListener(node, new Vote(votes, proposals, node.getId()));

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

