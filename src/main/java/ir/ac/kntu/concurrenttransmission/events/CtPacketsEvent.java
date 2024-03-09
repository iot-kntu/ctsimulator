package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.BaseSimEvent;
import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class CtPacketsEvent extends BaseSimEvent implements CtEvent {

    private final List<FloodPacket<?>> packets;

    public CtPacketsEvent(long time, FloodPacket<?> packet) {
        super(time);

        Objects.requireNonNull(packet);
        this.packets = new ArrayList<>();
        this.packets.add(packet);
    }

    public List<FloodPacket<?>> getPackets() {
        return packets;
    }

    public boolean areMessagesSimilar() {

        boolean areTheSame;

        CiMessage<?> first = packets.get(0).ciMessage();
        areTheSame = IntStream.range(1, packets.size())
                              .mapToObj(i -> packets.get(i).ciMessage())
                              .allMatch(ciMessage -> ciMessage.equals(first));

        return areTheSame;
    }

    @Override
    public void handle(ContextView context) {
        context.getApplication().ctPacketsReceived(this, context);
    }

    @Override
    public CtNode getReceiver() {
        return packets.get(0).receiver();
    }

    @Override
    public void addPacket(FloodPacket<?> packet) {
        Objects.requireNonNull(packet);

        if (!packet.receiver().equals(getReceiver()))
            throw new IllegalArgumentException("The receiver of the packet is different");

        packets.add(packet);
    }
}
