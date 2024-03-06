package ir.ac.kntu.concurrenttransmission;

import ir.ac.kntu.concurrenttransmission.blueflood.*;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
                    BlueFloodApplication.DEFAULT_INTERFERENCE_PROB,
                    5      // rounds limit
            );

            BlueFloodStrategies strategies = new BlueFloodStrategies(
                    new RoundRobin(netGraph.getNodeCount()),    //initiator strategy
                    new DefaultTransmissionPolicy(3, netGraph)  // flood repeat count
            );

            BlueFloodApplication blueFloodApplication = new BlueFloodApplication(settings, strategies);
            blueFloodApplication.setListener(new BlueFloodListener() {
                @Override
                public void ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets) {

                }

                @Override
                public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets,
                                          boolean arePacketsSimilar) {

                }

                @Override
                public CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
                    return null;
                }

                @Override
                public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage,
                                               int whichRepeat) {
                    return null;
                }
            });

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

