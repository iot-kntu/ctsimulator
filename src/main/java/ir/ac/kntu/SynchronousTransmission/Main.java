package ir.ac.kntu.SynchronousTransmission;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.nio.json.JSONExporter;
import org.jgrapht.nio.json.JSONImporter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class Main {

    public static void main(String[] args) {

        try {
            startLogger();

            JSONExporter<Node,DefaultEdge> jsonExporter = new JSONExporter<>();

            //JSONImporter<Node>
            DefaultUndirectedGraph<Node, DefaultEdge> graph = new DefaultUndirectedGraph<>(DefaultEdge.class);
            //graph.addVertex(new Node())

            SimulationSettings settings = new SimulationSettings(
                    1,    //flood repeat
                    StInitiatorStrategy.RoundRobin, //initiator strategy
                    0.0   // loss probability
            );

            NetGraph netGraph = new NetGraph(graph);

            StSimulator simulator = StSimulator.createInstance(settings, netGraph, new NopStApplication());
            simulator.start();
        }
        catch (Exception e) {
            System.err.println("High level error occurred: " + e);
        }
    }

    public static void startLogger() throws IOException {
        InputStream inputStream = ClassLoader.getSystemClassLoader()
                                             .getResourceAsStream("logging.properties");

        if (inputStream == null) {
            System.err.println("Cannot find logging config file, is package corrupted??");
            System.exit(2);
        }

        File logDir = new File("logs").getAbsoluteFile();
        if(!logDir.exists())
            logDir.mkdir();

        LogManager.getLogManager().readConfiguration(inputStream);
    }

}

