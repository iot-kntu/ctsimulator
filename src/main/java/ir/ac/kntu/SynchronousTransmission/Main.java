package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.*;
import ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies.NonFaultyFloodStrategy;
import ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies.SilentAndFaultyFloodStrategy;
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
                    new RoundRobin(netGraph.getNodeCount()),    //initiator strategy
                    new DefaultTransmissionPolicy(3, netGraph),  // flood repeat count
                    new SimpleStringBlueFloodMessageBuilder()
            );

            BlueFloodBaseApplication blueFloodApplication = new BlueFloodBaseApplication(settings, strategies);

            blueFloodApplication.setFloodStrategy(NodeFloodStrategyType.Normal, new NonFaultyFloodStrategy());
            blueFloodApplication.setFloodStrategy(NodeFloodStrategyType.Silent, new SilentFloodStrategy());
            blueFloodApplication.setFloodStrategy(NodeFloodStrategyType.Silent, new SilentFloodStrategy());
            blueFloodApplication.setFloodStrategy(NodeFloodStrategyType.SilentAndFaulty,
                                                  new SilentAndFaultyFloodStrategy(0.3) {
                                                      @Override
                                                      protected <T> CiMessage<T> createFaultyMessage(ContextView context, Node sender, CiMessage<T> message, int whichRepeat) {

                                                          final CiMessage<String> ciMessage = new CiMessage<>(
                                                                  message.initiator(),
                                                                  "  ");

                                                          return (CiMessage<T>) ciMessage;
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

