package ir.ac.kntu.concurrenttransmission;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class NetGraph {

    public static final NetGraph EMPTY_GRAPH = new NetGraph();

    private final List<CtNode> nodes = new ArrayList<>();
    private final HashMap<CtNode, List<CtNode>> neighborsMap = new HashMap<>();
    private int diameter = 0;

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

            final HashMap<CtNode, List<CtNode>> map = netGraph.neighborsMap;
            for (CtNode keyNode : map.keySet()) {
                final List<CtNode> neighbors = netGraph.neighborsMap.get(keyNode);
                for (CtNode neighbor : neighbors) {
                    if (!netGraph.neighborsMap.get(neighbor).contains(keyNode))
                        throw new IllegalStateException(
                                "Node " + keyNode + " has edge to " + neighbor + " but the reverse is not true");
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
        if (diameter <= 0) {
            int grahDepth = 0;
            int maxTries = getNodeCount() % 2 + getNodeCount() / 2;
            int visitedRoots = 0;

            for (CtNode node : nodes) {
                final int maxDepth = new GraphDepthFinder(this, node).getMaxDepth();
                if (maxDepth > grahDepth)
                    grahDepth = maxDepth;

                visitedRoots++;
                if (visitedRoots >= maxTries)
                    break;
            }
            diameter = grahDepth;
        }

        return diameter;
    }
}

class GraphDepthFinder {

    private final NetGraph graph;
    private final CtNode root;

    private final Deque<CtNode> nodeStack;
    // to keep visited state
    private final HashMap<CtNode, NodeHist> nodeDepthMap = new HashMap<>();

    public GraphDepthFinder(NetGraph graph, CtNode root) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(root);

        this.graph = graph;
        this.root = root;

        this.nodeStack = new ArrayDeque<>(graph.getNodeCount());
    }

    public int getMaxDepth() {

        int maxDepth = 0;
        nodeStack.add(root);
        nodeDepthMap.put(root, new NodeHist(root, null, Collections.emptyList(), 0));

        while (!nodeStack.isEmpty()) {

            final CtNode currentNode = nodeStack.removeFirst();
            final NodeHist nodeHist = nodeDepthMap.get(currentNode);

            final List<CtNode> neighbors = graph.getNodeNeighbors(currentNode);
            int newDepth = nodeHist.depth() + 1;

            for (CtNode neighbor : neighbors) {

                if (neighbor.equals(nodeHist.parent()) || nodeHist.visitedParents().contains(neighbor))
                    continue;

                final NodeHist neighborHist = nodeDepthMap.get(neighbor);

                if (neighborHist == null) {
                    nodeStack.addLast(neighbor);
                    nodeDepthMap.put(neighbor, new NodeHist(neighbor, currentNode, Collections.emptyList(), newDepth));
                }
                // neighborHist!= null &&
                else if (neighborHist.depth() > newDepth) {
                    // we visit a node that was visited before
                    // from a different path that had a lower depth
                    nodeStack.addLast(neighbor);

                    // merge all previous visited parent to prevent loop
                    List<CtNode> allPrevParents = new ArrayList<>(nodeHist.visitedParents());
                    allPrevParents.add(nodeHist.parent());

                    nodeDepthMap.put(neighbor, new NodeHist(neighbor, currentNode, allPrevParents, newDepth));
                }
            }

            if (newDepth > maxDepth)
                maxDepth = newDepth;
        }

        return maxDepth - 1;
    }

    /**
     * History of a visited node
     *
     * @param node
     * @param parent
     * @param visitedParents
     * @param depth
     */
    record NodeHist(CtNode node, CtNode parent, List<CtNode> visitedParents, int depth) {
    }

}
