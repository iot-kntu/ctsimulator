package ir.ac.kntu.concurrenttransmission;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class NetGraph {

    public static final NetGraph EMPTY_GRAPH = new NetGraph();

    private final List<CtNode> nodes = new ArrayList<>();
    private final HashMap<CtNode, List<CtNode>> neighborsMap = new HashMap<>();

    private NetGraph() {
    }

    public static @NotNull NetGraph loadFrom(String path) throws Exception {
        NetGraph netGraph = new NetGraph();

        HashMap<CtNode, List<Integer>> nodeNeighborsId = new HashMap<>();

        int lineCounter = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineCounter++;

                if (line.startsWith("#"))
                    continue;

                final String[] splitWithSemicolon = line.split(";");
                if (splitWithSemicolon.length < 3)
                    throw new IllegalStateException(
                            "Line " + lineCounter + ": each row must have format of <node_id>;<Node " +
                                    "Type>;<comma separated list of neighbors>");

                final int nodeId = Integer.parseInt(splitWithSemicolon[0]);

                final String className = splitWithSemicolon[2];
                final Class<?> nodeClass = Class.forName(
                        "ir.ac.kntu.concurrenttransmission.blueflood.nodes." + className);
                final CtNode instance = (CtNode) nodeClass.getDeclaredConstructor(Integer.class).newInstance(nodeId);

                netGraph.nodes.add(instance);
                netGraph.neighborsMap.put(instance, new ArrayList<>());

                final ArrayList<Integer> neighbors = new ArrayList<>();
                nodeNeighborsId.put(instance, neighbors);

                final String[] neighborsStr = splitWithSemicolon[1].split(",");
                for (String s : neighborsStr) {
                    if (s.isBlank())
                        continue;
                    final int neighborId = Integer.parseInt(s);
                    neighbors.add(neighborId);
                }

            }

            for (CtNode node : netGraph.getNodes()) {
                final List<Integer> ids = nodeNeighborsId.get(node);
                for (Integer id : ids) {
                    final CtNode nodeById = netGraph.getNodeById(id);
                    if (nodeById == null) {
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
            for (CtNode node : nodes) {

                fileWriter.write(node.getId());
                fileWriter.write(";");

                for (CtNode node1 : neighborsMap.get(node))
                    fileWriter.write(node1.getId() + ",");

                fileWriter.write(";");

                fileWriter.write(node.getClass().getSimpleName());

                fileWriter.newLine();
            }
        }
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public CtNode getNodeById(int nodeId) {

        final List<CtNode> list = nodes.stream().filter(node -> node.getId() == nodeId).toList();
        if (list.isEmpty())
            return CtNode.NULL_NODE;

        return list.get(0);
    }

    public List<CtNode> getNodeNeighbors(CtNode node) {
        return (node == null || node.getId() < 0) ? Collections.emptyList() : neighborsMap.get(node);
    }

    public List<CtNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public int getDiameter() {
        //fixme put algorithm here
        return 2;
    }
}
