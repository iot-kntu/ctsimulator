package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.applications.NodeFaultMode;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class NetGraph {

    private final List<Node> nodes = new ArrayList<>();
    private final HashMap<Node, List<Node>> neighborsMap = new HashMap<>();

    private NetGraph() {
    }

    public static @NotNull NetGraph loadFrom(String path) throws Exception {
        NetGraph netGraph = new NetGraph();

        int lineCounter = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineCounter++;

                if (line.startsWith("#"))
                    continue;

                final String[] split1 = line.split(";");
                if (split1.length < 3)
                    throw new IllegalStateException(
                            "Line " + lineCounter + ": each row must have format of <node_id>;<Node " +
                                    "Type>;<comma separated list of neighbors>");

                final int nodeId = Integer.parseInt(split1[0]);
                final Node newNode = new Node(nodeId);
                netGraph.nodes.add(newNode);
                netGraph.neighborsMap.put(newNode, new ArrayList<>());

                final int modeNo = Integer.parseInt(split1[1]);
                newNode.setFaultMode(NodeFaultMode.values()[modeNo]);

                final String[] neighborsStr = split1[2].split(",");
                for (String s : neighborsStr) {
                    if (s.isBlank())
                        continue;
                    final int neighborId = Integer.parseInt(s);
                    netGraph.neighborsMap.get(newNode).add(new Node(neighborId));
                }
            }
        }
        catch (Exception ex) {
            throw new Exception("Error parsing graph file at line " + lineCounter, ex);
        }

        return netGraph;
    }

    public void saveGraph(String path) throws IOException {

        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(path))) {
            for (Node node : nodes) {

                fileWriter.write(node.getId());
                fileWriter.write(";");

                fileWriter.write(node.getFaultMode().ordinal());
                fileWriter.write(";");

                final List<Node> neighbors = neighborsMap.get(node);
                neighbors.forEach(node1 -> {
                    try {
                        fileWriter.write(node1.getId() + ",");
                        fileWriter.newLine();
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public Node getNodeById(int nodeId) {

        final List<Node> list = nodes.stream().filter(node -> node.getId() == nodeId).toList();
        if (list.isEmpty())
            return Node.NULL_NODE;

        return list.get(0);
    }

    public List<Node> getNodeNeighbors(Node node) {
        return neighborsMap.get(node);
    }

}
