package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.ConcurrentTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.ChaosNodeState;

import java.util.*;

/**
 * This class intended for logging the state of the simulation and printing
 */
public class ChaosStateLogger {

    private final SortedMap<CtNetworkTime, Map<CtNode, ChaosNodeState>> history = new TreeMap<>();
    private final List<CtNode> nodes;
    private final ConcurrentTransmissionPolicy transmissionPolicy;

    public ChaosStateLogger(List<CtNode> nodes, ConcurrentTransmissionPolicy transmissionPolicy) {
        this.nodes = new ArrayList<>(nodes);
        this.nodes.sort(Comparator.comparingInt(CtNode::getId));
        this.transmissionPolicy = transmissionPolicy;
    }

    public void setState(CtNetworkTime time, CtNode node, ChaosNodeState nodeState) {
        final Map<CtNode, ChaosNodeState> stateMap = history.computeIfAbsent(time, k -> new TreeMap<>());
        stateMap.put(node, nodeState);
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
        Map<CtNode, ChaosNodeState> lastKnownStates = new HashMap<>();
        ChaosNodeState lastKnownState = null;

        for (CtNode node : this.nodes) {
            builder.append(String.format("N[%-3d]|", node.getId()));
            for (int r = 0; r <= maxRound; r++) {
                for (int s = 0; s < totalSlotsOfRound; s++) {
                    CtNetworkTime currentTime = new CtNetworkTime(r, s);

                    if (history.containsKey(currentTime) && history.get(currentTime).containsKey(node)) {
                        lastKnownStates.put(node, history.get(currentTime).get(node));
                        lastKnownState =  history.get(currentTime).get(node);
                    } else {
                        lastKnownStates.put(node, lastKnownState);
                    }

                    ChaosNodeState stateToPrint = lastKnownStates.get(node);
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