package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.*;
import ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies.SilentFloodStrategy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class Main {

    public static void main(String[] args) {

        try {
            startLogger();

            final NetGraph netGraph = NetGraph.loadFrom("sample.graph");

            if (netGraph.isEmpty())
                throw new IllegalArgumentException("Invalid graph file format, it is not loaded");

            BlueFloodSettings settings = new BlueFloodSettings(
                    0.0,
                    NodeFloodStrategy.DEFAULT_INTERFERENCE_PROB,
                    5      // rounds limit
            );

            BlueFloodStrategies strategies = new BlueFloodStrategies(
                    new RoundRobin(netGraph.getNodeCount()), //initiator strategy
                    new DefaultTransmissionPolicy(3, netGraph)
            );

            BlueFloodBaseApplication blueFloodApplication = new BlueFloodBaseApplication(settings, strategies) {

                static int counter = 1;

                @Override
                public CiMessage<String> buildMessage(ContextView context) {
                    String msg = "MSG-" + strategies.initiatorStrategy().getCurrentInitiatorId() + "-" + counter++;
                    return new CiMessage<>(netGraph.getNodeById(strategies.initiatorStrategy().getCurrentInitiatorId()),
                                           msg);
                }
            };

            blueFloodApplication.setFloodStrategy(NodeFloodStrategyType.Silent, new SilentFloodStrategy());

            CtSimulator simulator = CtSimulator.createInstance(netGraph, blueFloodApplication);
            simulator.start();

            System.out.println(blueFloodApplication.printTimeline());
        }
        catch (Exception e) {
            System.err.println("High level error occurred: " + e);
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

