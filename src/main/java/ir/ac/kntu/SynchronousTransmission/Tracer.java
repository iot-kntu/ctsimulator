package ir.ac.kntu.SynchronousTransmission;

import java.util.*;

public class Tracer {

    private final SortedMap<SimPace, Map<Integer, NodeState>> nodeStates = new TreeMap<>();
    private final List<Node> nodes;

    public Tracer(List<Node> nodes) {
        this.nodes = nodes;
    }

    public void timeForward(int round, int slot) {

        Map<Integer, NodeState> nodeStateMap = new HashMap<>();

        for (Node node : nodes)
            nodeStateMap.put(node.getId(), NodeState.Sleep);

        final SimPace pace = new SimPace(round, slot);
        nodeStates.put(pace, nodeStateMap);
    }

    public void setState(int round, int slot, Node node, NodeState nodeState) {
        SimPace simPace = new SimPace(round, slot);
        final Map<Integer, NodeState> stateMap = nodeStates.get(simPace);

        if (stateMap == null)
            throw new IllegalStateException("No state saved for this round and slot");

        stateMap.put(node.getId(), nodeState);
    }

    public String printTimeline() {

        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%1$5s", "*"));

        for (SimPace simPace : nodeStates.keySet())
            builder.append(String.format("%1$5s", simPace.round()));
        builder.append('\n');

        builder.append(String.format("%1$5s", "*"));
        for (SimPace simPace : nodeStates.keySet())
            builder.append(String.format("%1$5s", simPace.slot()));
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

    record SimPace(int round, int slot) implements Comparable<SimPace> {
        @Override
        public int compareTo(SimPace o) {

            return this.round() == o.round()
                    ? this.slot() - o.slot()
                    : this.round() - o.round();
        }
    }

}
