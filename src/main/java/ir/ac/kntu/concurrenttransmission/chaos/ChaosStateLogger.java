package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;

import java.util.*;

/**
 * Logs and prints simulation state dynamically based on ChaosDefaultTransmissionPolicy rounds.
 */
public class ChaosStateLogger {

    private final SortedMap<CtNetworkTime, Map<CtNode, String>> history = new TreeMap<>();
    private final SortedMap<CtNetworkTime, Map<CtNode, String>> knowledgeHistory = new TreeMap<>();
    private final List<CtNode> nodes;
    private final ChaosTransmissionPolicy transmissionPolicy;

    public ChaosStateLogger(List<CtNode> nodes, ChaosTransmissionPolicy transmissionPolicy) {
        this.nodes = new ArrayList<>(nodes);
        this.nodes.sort(Comparator.comparingInt(CtNode::getId));
        this.transmissionPolicy = transmissionPolicy;
    }

    public void setState(CtNetworkTime time, CtNode node, String nodeState) {
        final Map<CtNode, String> stateMap = history.computeIfAbsent(time, k -> new TreeMap<>());
        stateMap.put(node, nodeState);
    }

    public void setKnowledge(CtNetworkTime time, CtNode node, String knowledge) {
        final Map<CtNode, String> knowledgeMap = knowledgeHistory.computeIfAbsent(time, k -> new TreeMap<>());
        knowledgeMap.put(node, knowledge);
    }

    /**
     * Returns a defensive copy of the collected node states keyed by network time.
     */
    public SortedMap<CtNetworkTime, Map<CtNode, String>> snapshotHistory() {
        SortedMap<CtNetworkTime, Map<CtNode, String>> copy = new TreeMap<>();
        history.forEach((time, stateMap) -> copy.put(time, Collections.unmodifiableMap(new HashMap<>(stateMap))));
        return Collections.unmodifiableSortedMap(copy);
    }

    /**
     * Returns a defensive copy of the collected node knowledge keyed by network time.
     */
    public SortedMap<CtNetworkTime, Map<CtNode, String>> snapshotKnowledgeHistory() {
        SortedMap<CtNetworkTime, Map<CtNode, String>> copy = new TreeMap<>();
        knowledgeHistory.forEach((time, knowledgeMap) -> copy.put(time, Collections.unmodifiableMap(new HashMap<>(knowledgeMap))));
        return Collections.unmodifiableSortedMap(copy);
    }

    public String printTimeline() {
        if (history.isEmpty()) {
            return "StateHistory is empty.";
        }

        int roundCount = transmissionPolicy.getTotalRounds();
        StringBuilder builder = new StringBuilder();

        // --- Print Rounds Header ---
        builder.append(String.format("%-6s|", "R"));
        for (int r = 0; r <= roundCount; r++) {
            long roundLength = transmissionPolicy.getRoundDuration(r);
            for (int s = 0; s < roundLength; s++) {
                builder.append(String.format("%-5s", r));
            }
            builder.append("|");
        }
        builder.append('\n');

        // --- Print Slots Header ---
        builder.append(String.format("%-6s|", "S"));
        for (int r = 0; r <= roundCount; r++) {
            long roundLength = transmissionPolicy.getRoundDuration(r);
            for (int s = 0; s < roundLength; s++) {
                builder.append(String.format("%-5s", s));
            }
            builder.append("|");
        }
        builder.append('\n');

        // --- Print Separator ---
        builder.append(String.format("%-6s|", "------"));
        for (int r = 0; r <= roundCount; r++) {
            long roundLength = transmissionPolicy.getRoundDuration(r);
            for (int s = 0; s < roundLength; s++) {
                builder.append("-----");
            }
            builder.append("|");
        }
        builder.append('\n');

        // --- Print Nodes Status ---
        Map<CtNode, String> lastKnownStates = new HashMap<>();

        for (CtNode node : this.nodes) {
            builder.append(String.format("N[%-3d]|", node.getId()));

            for (int r = 0; r <= roundCount; r++) {
                long roundLength = transmissionPolicy.getRoundDuration(r);

                for (int s = 0; s < roundLength; s++) {
                    CtNetworkTime currentTime = new CtNetworkTime(r, s);

                    String nodeState = null;
                    if (history.containsKey(currentTime) && history.get(currentTime).containsKey(node)) {
                        nodeState = history.get(currentTime).get(node);
                        lastKnownStates.put(node, nodeState);
                    } else {
                        nodeState = lastKnownStates.get(node);
                    }

                    String symbol = (nodeState != null) ? nodeState : ".";
                    builder.append(String.format("%-5s", symbol));
                }
                builder.append("|");
            }
            builder.append('\n');
        }

        return builder.toString();
    }
}
