package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.BlueFloodBaseApplication;
import ir.ac.kntu.SynchronousTransmission.blueflood.BlueFloodConfig;
import ir.ac.kntu.SynchronousTransmission.blueflood.BlueFloodDefaultTransmissionPolicy;

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

            BlueFloodConfig config = new BlueFloodConfig(
                    0.0,    // loss probability
                    5,      // rounds limit
                    new RoundRobin(netGraph.getNodeCount()), //initiator strategy
                    new BlueFloodDefaultTransmissionPolicy(2, netGraph)
            );


            BlueFloodBaseApplication blueFloodApplication = new BlueFloodBaseApplication(config){
                static int counter = 1;
                @Override
                public StMessage<String> buildMessage(ContextView context) {
                    String msg = "MSG-" + config.initiatorStrategy().getCurrentInitiatorId() + "-" + counter++;
                    return new StMessage<>(netGraph.getNodeById(config.initiatorStrategy().getCurrentInitiatorId()), msg);
                }
            };

            StSimulator simulator = StSimulator.createInstance(netGraph, blueFloodApplication);
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

