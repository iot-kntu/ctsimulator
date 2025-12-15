package ir.ac.kntu.concurrenttransmission;

import java.util.*;

/**
 * This class intended for logging the state of the simulation and printing
 */
public class StateLogger {

    private final SortedMap<CtNetworkTime, Map<CtNode, NodeState>> history = new TreeMap<>();
    private final List<CtNode> nodes;
    private final ConcurrentTransmissionPolicy transmissionPolicy;

    public StateLogger(List<CtNode> nodes, ConcurrentTransmissionPolicy transmissionPolicy) {
        this.nodes = new ArrayList<>(nodes);
        this.nodes.sort(Comparator.comparingInt(CtNode::getId));
        this.transmissionPolicy = transmissionPolicy;
    }

    public void setState(CtNetworkTime time, CtNode node, NodeState nodeState) {
        final Map<CtNode, NodeState> stateMap = history.computeIfAbsent(time, k -> new TreeMap<>());
        stateMap.put(node, nodeState);
    }

    /**
     * Returns a defensive copy of the collected node states keyed by network time.
     */
    public SortedMap<CtNetworkTime, Map<CtNode, NodeState>> snapshotHistory() {
        SortedMap<CtNetworkTime, Map<CtNode, NodeState>> copy = new TreeMap<>();
        history.forEach((time, stateMap) -> copy.put(time, Collections.unmodifiableMap(new HashMap<>(stateMap))));
        return Collections.unmodifiableSortedMap(copy);
    }

    public String printTimeline() {
        if (history.isEmpty()) {
            return "StateHistory is empty.";
        }

        StringBuilder builder = new StringBuilder();
        int totalSlotsOfRound = transmissionPolicy.getTotalSlotsOfRound();
        int maxRound = history.lastKey().round();

        // --- Print Rounds Header ---
        builder.append(String.format("%-6s|", "R"));
        for (int r = 0; r <= maxRound; r++) {
            for (int s = 0; s < totalSlotsOfRound; s++) {
                builder.append(String.format("%-5s", r));
            }
            builder.append(String.format("%-5s", "|"));
        }
        builder.append('\n');

        // --- Print Slots Header ---
        builder.append(String.format("%-6s|", "S"));
        for (int r = 0; r <= maxRound; r++) {
            for (int s = 0; s < totalSlotsOfRound; s++) {
                builder.append(String.format("%-5s", s));
            }
            builder.append(String.format("%-5s", "|"));
        }
        builder.append('\n');

        // --- Print Separator ---
        builder.append(String.format("%-6s|", "------"));
        for (int r = 0; r <= maxRound; r++) {
            for (int s = 0; s < totalSlotsOfRound; s++) {
                builder.append(String.format("%-5s", "-----"));
            }
            builder.append(String.format("%-5s", "|"));
        }
        builder.append('\n');

        // --- Print Nodes Status ---
        Map<CtNode, NodeState> lastKnownStates = new HashMap<>();

        for (CtNode node : this.nodes) {
            builder.append(String.format("N[%-3d]|", node.getId()));
            for (int r = 0; r <= maxRound; r++) {
                for (int s = 0; s < totalSlotsOfRound; s++) {
                    CtNetworkTime currentTime = new CtNetworkTime(r, s);

                    if (history.containsKey(currentTime) && history.get(currentTime).containsKey(node)) {
                        lastKnownStates.put(node, history.get(currentTime).get(node));
                    } else {
                        lastKnownStates.put(node, null);
                    }

                    NodeState stateToPrint = lastKnownStates.get(node);
                    char symbol = (stateToPrint != null) ? stateToPrint.getSymbol() : '.';
                    builder.append(String.format("%-5c", symbol));
                }
                builder.append(String.format("%-5s", "|"));
            }
            builder.append('\n');
        }
        return builder.toString();
    }
}
