package ir.ac.kntu.SynchronousTransmission;

import java.util.*;

/**
 * This class intended for logging the state of the simulation and printing
 */
public class StateLogger {

    private final SortedMap<CtNetworkTime, Map<Integer, NodeState>> nodeStates = new TreeMap<>();
    private final List<Node> nodes;

    public StateLogger(List<Node> nodes) {
        this.nodes = nodes;
    }

    public void timeForward(CtNetworkTime networkTime) {

        Map<Integer, NodeState> nodeStateMap = new HashMap<>();

        for (Node node : nodes)
            nodeStateMap.put(node.getId(), NodeState.Listen);

        nodeStates.put(networkTime, nodeStateMap);
    }

    public void setState(CtNetworkTime networkTime, Node node, NodeState nodeState) {

        final Map<Integer, NodeState> stateMap = nodeStates.get(networkTime);

        if (stateMap == null)
            throw new IllegalStateException("No state saved for this round and slot");

        stateMap.put(node.getId(), nodeState);
    }

    public String printTimeline() {

        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%1$5s", "*"));

        for (CtNetworkTime CtNetworkTime : nodeStates.keySet())
            builder.append(String.format("%1$5s", CtNetworkTime.round()));
        builder.append('\n');

        builder.append(String.format("%1$5s", "*"));
        for (CtNetworkTime CtNetworkTime : nodeStates.keySet())
            builder.append(String.format("%1$5s", CtNetworkTime.slot()));
        builder.append('\n');

        final List<String> rows = new ArrayList<>();

        for (Node node : nodes)
            rows.add(String.format("%1$5s", "N[" + node.getId() + "]"));

        for (Map<Integer, NodeState> valueMap : nodeStates.values()) {
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                rows.set(i, rows.get(i) + String.format("%1$5c", valueMap.get(node.getId()).getSymbol()));
            }
        }

        rows.forEach(s -> builder.append(s).append("\n"));

        return builder.toString();
    }
}
