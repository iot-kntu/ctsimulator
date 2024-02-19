package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.NodeFloodStrategyType;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class NetGraph {

    public static final NetGraph EMPTY_GRAPH = new NetGraph();

    private final List<Node> nodes = new ArrayList<>();
    private final HashMap<Node, List<Node>> neighborsMap = new HashMap<>();

    private NetGraph() {
    }

    public static @NotNull NetGraph loadFrom(String path) throws Exception {
        NetGraph netGraph = new NetGraph();

        HashMap<Node, List<Integer>> nodeNeighborsId = new HashMap<>();

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
                newNode.setFloodStrategy(NodeFloodStrategyType.values()[modeNo]);

                final ArrayList<Integer> neighbors = new ArrayList<>();
                nodeNeighborsId.put(newNode, neighbors);

                final String[] neighborsStr = split1[2].split(",");
                for (String s : neighborsStr) {
                    if (s.isBlank())
                        continue;
                    final int neighborId = Integer.parseInt(s);
                    neighbors.add(neighborId);
                }
            }

            for (Node node : netGraph.getNodes()) {
                final List<Integer> ids = nodeNeighborsId.get(node);
                for (Integer id : ids) {
                    final Node nodeById = netGraph.getNodeById(id);
                    if(nodeById == null){
                        throw new IllegalStateException("Node by id of " + id + " not found");
                    }
                    netGraph.neighborsMap.get(node).add(nodeById);
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

                fileWriter.write(node.getFloodStrategy().ordinal());
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
        return (node.getId() < 0) ? Collections.emptyList() : neighborsMap.get(node);
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(nodes);
    }
    public int getDiameter(){
        //fixme put algoruthm here
        return 2;
    }
}
