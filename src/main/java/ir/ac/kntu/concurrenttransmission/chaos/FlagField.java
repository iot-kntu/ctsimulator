package ir.ac.kntu.concurrenttransmission.chaos;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A field that holds the flags for all nodes in the network.
 * This class replaces the simple BitSet to support different types of
 * NodeFlags.
 * This class is immutable.
 */
public record FlagField(Map<Integer, Flag> flags) {

    /**
     * Creates an empty FlagField.
     */
    public static FlagField empty() {
        return new FlagField(new TreeMap<>());
    }

    /**
     * Creates an initial FlagField for a single node.
     */
    public static FlagField initial(int nodeId, Flag initialFlag) {
        return new FlagField(Map.of(nodeId, initialFlag));
    }

    /**
     * Merges this flag field with another one.
     */
    public FlagField merge(FlagField other) {
        Map<Integer, Flag> mergedFlags = new TreeMap<>(this.flags);
        for (Map.Entry<Integer, Flag> entry : other.flags.entrySet()) {
            mergedFlags.merge(entry.getKey(), entry.getValue(), Flag::merge);
        }
        return new FlagField(mergedFlags);
    }

    /**
     * Gets the flag for a specific node.
     */
    public Flag getFlag(int nodeId) {
        return flags.get(nodeId);
    }

    /**
     * Returns the number of nodes that have a flag set in this field.
     */
    public int getParticipationCount() {
        return flags.size();
    }

    @Override
    public String toString() {
        // A more compact representation for logging
        return flags.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().toString())
                .collect(Collectors.joining(", ", "{", "}"));
    }
}