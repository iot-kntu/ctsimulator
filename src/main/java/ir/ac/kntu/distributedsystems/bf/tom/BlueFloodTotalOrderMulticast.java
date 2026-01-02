package ir.ac.kntu.distributedsystems.bf.tom;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Total order multicast for BlueFlood based on round sequencing.
 * Each round publishes exactly one ordered entry (data or NOOP) and
 * payloads carry deltas plus optional replies to missing requests.
 */
public class BlueFloodTotalOrderMulticast implements BlueFloodNodeListener {

    private static final Logger logger = Logger.getLogger(BlueFloodTotalOrderMulticast.class.getSimpleName());

    private final Queue<Object> outbound;
    private final Map<Integer, TotalOrderMessage> log = new HashMap<>();
    private final Set<Integer> floodedMessageNos = new HashSet<>();
    private final Set<Integer> pendingRequests = new HashSet<>();

    private int nextSequenceToDeliver = 0;
    private Integer lastInitiatedRound = null;
    private CtMessage<?> lastInitiationMessage = null;

    public BlueFloodTotalOrderMulticast(Queue<Object> outbound) {
        this.outbound = outbound != null ? outbound : new LinkedList<>();
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets,
                                     FloodPacket<?> selectedPacket, boolean areSimilar) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(selectedPacket);

        CtMessage<?> ctMessage = selectedPacket.ctMessage();
        if (ctMessage.isNull() || !(ctMessage.content() instanceof TotalOrderPayload payload)) {
            return false;
        }

        mergeEntries(payload.entries());
        recordRequests(payload.missingRequests());
        deliverAvailable(context, selectedPacket.receiver());

        return floodedMessageNos.add(ctMessage.messageNo());
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        logger.log(Level.INFO, "Packets lost for node " + packets.get(0).receiver().getId());
    }

    @Override
    public CtMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(initiator);

        int round = 0;
        if (context.getApplication().getNetworkTime() != null) {
            round = context.getApplication().getNetworkTime().round();
        }

        if (lastInitiatedRound != null && lastInitiatedRound == round && lastInitiationMessage != null) {
            return lastInitiationMessage;
        }

        TotalOrderMessage entry = nextEntry(round);
        mergeEntry(entry);

        Set<Integer> missingRequests = findMissingSequences(round);
        List<TotalOrderMessage> outgoingEntries = buildOutgoingEntries(entry);

        TotalOrderPayload payload = new TotalOrderPayload(outgoingEntries, missingRequests);
        CtMessage<?> message = new CtMessage<>(initiator, payload);

        lastInitiatedRound = round;
        lastInitiationMessage = message;

        deliverAvailable(context, initiator);

        return message;
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender,
                                   CtMessage<?> receivedMessage, int whichRepeat) {
        return receivedMessage;
    }

    private TotalOrderMessage nextEntry(int sequence) {
        Object value = outbound.poll();
        if (value == null) {
            return TotalOrderMessage.noop(sequence);
        }
        return TotalOrderMessage.data(sequence, value);
    }

    private void mergeEntries(List<TotalOrderMessage> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        entries.forEach(this::mergeEntry);
    }

    private void mergeEntry(TotalOrderMessage incoming) {
        if (incoming == null) {
            return;
        }
        log.putIfAbsent(incoming.sequence(), incoming);
    }

    private void recordRequests(Set<Integer> missingRequests) {
        if (missingRequests == null || missingRequests.isEmpty()) {
            return;
        }
        pendingRequests.addAll(missingRequests);
    }

    private List<TotalOrderMessage> buildOutgoingEntries(TotalOrderMessage primary) {
        List<TotalOrderMessage> outgoing = new ArrayList<>();
        Set<Integer> included = new HashSet<>();

        if (primary != null) {
            outgoing.add(primary);
            included.add(primary.sequence());
        }

        if (!pendingRequests.isEmpty()) {
            List<Integer> satisfied = new ArrayList<>();
            for (Integer sequence : pendingRequests) {
                TotalOrderMessage entry = log.get(sequence);
                if (entry != null && included.add(sequence)) {
                    outgoing.add(entry);
                    satisfied.add(sequence);
                }
            }
            pendingRequests.removeAll(satisfied);
        }

        return outgoing;
    }

    private Set<Integer> findMissingSequences(int currentRound) {
        Set<Integer> missing = new HashSet<>();
        for (int sequence = nextSequenceToDeliver; sequence < currentRound; sequence++) {
            if (!log.containsKey(sequence)) {
                missing.add(sequence);
            }
        }
        return missing;
    }

    private void deliverAvailable(ContextView context, CtNode receiver) {
        while (true) {
            TotalOrderMessage entry = log.get(nextSequenceToDeliver);
            if (entry == null) {
                return;
            }
            if (!entry.isNoop()) {
                logger.log(Level.INFO, String.format("%s::node[%d] delivered seq=%d value=%s",
                        context.getApplication().getNetworkTime(),
                        receiver.getId(),
                        entry.sequence(),
                        entry.value()));
            }
            nextSequenceToDeliver++;
        }
    }
}
