package ir.ac.kntu.SynchronousTransmission;

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

            SimulationSettings settings = new SimulationSettings(
                    1,    //flood repeat
                    StInitiatorStrategy.RoundRobin, //initiator strategy
                    0.0   // loss probability
            );

            StSimulator simulator = StSimulator.createInstance(settings, netGraph, new LimitedRoundStApplication(5));
            simulator.start();
        }
        catch (Exception e) {
            System.err.println("High level error occurred: " + e);
        }
    }

    static void startLogger() throws IOException {
        InputStream inputStream = ClassLoader.getSystemClassLoader()
                                             .getResourceAsStream("logging.properties");

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

