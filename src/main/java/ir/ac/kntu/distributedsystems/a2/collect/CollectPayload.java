package ir.ac.kntu.distributedsystems.a2.collect;

import java.util.Map;
import java.util.TreeMap;

/**
 * Represents the payload for a Collect message in Chaos.
 * It contains a map of node IDs to their respective data objects.
 */
public record CollectPayload(Map<Integer, Object> dataMap) {

    /**
     * Creates a new payload by merging the current data map with a received one.
     * @param other The payload from the received packet.
     * @return A new, merged CollectPayload.
     */
    public CollectPayload merge(CollectPayload other) {
        Map<Integer, Object> mergedData = new TreeMap<>(this.dataMap);
        other.dataMap.forEach(mergedData::putIfAbsent);
        return new CollectPayload(mergedData);
    }

    @Override
    public String toString() {
        return dataMap.toString();
    }
}