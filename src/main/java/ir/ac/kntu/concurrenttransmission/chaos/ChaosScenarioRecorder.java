package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.metrics.FailureReason;

import java.util.*;

/**
 * Collects metadata and events during a Chaos simulation to be used for report generation.
 */
public class ChaosScenarioRecorder {

    private static final String DEFAULT_NAME = "Unnamed Scenario";
    private static final String DEFAULT_AUTHOR = "Unknown Author";
    private static final String DEFAULT_DESCRIPTION = "No description provided.";

    private ScenarioMetadata metadata = new ScenarioMetadata(DEFAULT_NAME, DEFAULT_AUTHOR, DEFAULT_DESCRIPTION);
    private final SortedMap<CtNetworkTime, List<TransmissionEvent>> events = new TreeMap<>();
    private final Map<Integer, RoundInfo> roundMetadata = new HashMap<>();

    public void setScenarioMetadata(String name, String author, String description) {
        this.metadata = new ScenarioMetadata(
                normalize(name, DEFAULT_NAME),
                normalize(author, DEFAULT_AUTHOR),
                normalize(description, DEFAULT_DESCRIPTION)
        );
    }

    public ScenarioMetadata getMetadata() {
        return metadata;
    }

    public void setRoundMetadata(int roundIndex, String label, String description) {
        roundMetadata.put(roundIndex, new RoundInfo(
                normalize(label, "Round " + (roundIndex + 1)),
                normalize(description, "Auto generated description for round " + (roundIndex + 1))
        ));
    }

    public RoundInfo resolveRoundInfo(int roundIndex) {
        return roundMetadata.computeIfAbsent(roundIndex,
                idx -> new RoundInfo("Round " + (idx + 1), "Auto generated description for round " + (idx + 1)));
    }

    public void recordEvent(CtNetworkTime time, TransmissionEvent event) {
        if (time == null || event == null) {
            return;
        }
        events.computeIfAbsent(time, key -> new ArrayList<>()).add(event);
    }

    public SortedMap<CtNetworkTime, List<TransmissionEvent>> snapshotEvents() {
        SortedMap<CtNetworkTime, List<TransmissionEvent>> copy = new TreeMap<>();
        events.forEach((time, list) -> copy.put(time, List.copyOf(list)));
        return Collections.unmodifiableSortedMap(copy);
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public record ScenarioMetadata(String name, String author, String description) {
    }

    public record RoundInfo(String label, String description) {
    }

    public record TransmissionEvent(String type, int from, int to, boolean success, FailureReason failureReason,
                                    PacketSnapshot packet) {

        public static TransmissionEvent success(String type, FloodPacket<?> packet) {
            return fromPacket(type, packet, true, null);
        }

        public static TransmissionEvent failure(String type, FloodPacket<?> packet) {
            return fromPacket(type, packet, false, null);
        }

        public static TransmissionEvent failure(String type, FloodPacket<?> packet, FailureReason reason) {
            return fromPacket(type, packet, false, reason);
        }

        public static TransmissionEvent failure(String type, int from, int to, FailureReason reason) {
            return new TransmissionEvent(type, from, to, false, reason, null);
        }

        private static TransmissionEvent fromPacket(String type, FloodPacket<?> packet, boolean success,
                                                    FailureReason reason) {
            if (packet == null) {
                return new TransmissionEvent(type, -1, -1, success, reason, null);
            }
            int from = packet.sender() != null ? packet.sender().getId() : -1;
            int to = packet.receiver() != null ? packet.receiver().getId() : -1;
            return new TransmissionEvent(type, from, to, success, reason, PacketSnapshot.from(packet));
        }
    }

    public record PacketSnapshot(long time, Integer initiatorId, Integer messageNo, String content) {

        private static PacketSnapshot from(FloodPacket<?> packet) {
            if (packet == null) {
                return null;
            }
            CtMessage<?> message = packet.ctMessage();
            if (message == null || message.isNull()) {
                return new PacketSnapshot(packet.time(), null, null, null);
            }

            CtNode initiator = message.initiator();
            Integer initiatorId = (initiator == null || initiator.equals(CtNode.NULL_NODE)) ? null : initiator.getId();
            return new PacketSnapshot(packet.time(), initiatorId, message.messageNo(), serializeContent(message.content()));
        }

        private static String serializeContent(Object content) {
            if (content == null) {
                return null;
            }
            return content.toString();
        }
    }
}
