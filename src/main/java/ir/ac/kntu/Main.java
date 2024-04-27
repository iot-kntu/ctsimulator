package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.CtSimulator;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.RoundRobinInitiatorStrategy;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodApplication;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodSettings;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodStrategies;
import ir.ac.kntu.concurrenttransmission.blueflood.DefaultTransmissionPolicy;
import ir.ac.kntu.distributedsystems.fault.om.PrimaryBasedOralMessage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;


public class Main {

    public static void main(String[] args) {

        try {
            startLogger();

            //final NetGraph netGraph = NetGraph.loadFrom("sample.graph");
            final NetGraph netGraph = NetGraph.loadFrom("complex1.graph");
            final int graphDiameter = netGraph.getDiameter();
            System.out.println("graphDiameter = " + graphDiameter);
            System.out.println("================================");

            //noinspection
            if (netGraph.isEmpty())
                throw new IllegalArgumentException("Invalid graph file format, it is not loaded");

            BlueFloodSettings settings = new BlueFloodSettings(
                    0.0,
                    BlueFloodApplication.DEFAULT_INTERFERENCE_PROB,
                    5      // rounds limit
            );

            BlueFloodStrategies strategies = new BlueFloodStrategies(
                    new RoundRobinInitiatorStrategy(netGraph.getNodeCount()),    //initiator strategy
                    new DefaultTransmissionPolicy(3, netGraph)  // flood repeat count
            );

            BlueFloodApplication blueFloodApplication = new BlueFloodApplication(settings, strategies);
            netGraph.getNodes().forEach(node -> blueFloodApplication.setListener(node, new PrimaryBasedOralMessage()));

            CtSimulator simulator = CtSimulator.createInstance(netGraph, blueFloodApplication);
            simulator.start();

            System.out.println(blueFloodApplication.printTimeline());
        }
        catch (Exception e) {
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

