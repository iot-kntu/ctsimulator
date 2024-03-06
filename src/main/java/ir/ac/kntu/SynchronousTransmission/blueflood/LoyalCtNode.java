package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.BlueFloodSettings;
import ir.ac.kntu.SynchronousTransmission.CiMessage;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.CtNode;
import ir.ac.kntu.SynchronousTransmission.events.FloodPacket;

import java.util.List;
import java.util.Objects;

/**
 * A default implementation of a CtNode which behaves loyally
 * which means this CtNode acts non-faulty according to the given
 * TransmissionPolicy
 */
public class LoyalCtNode implements CtNode {

    private final int id;

    public LoyalCtNode(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public CiMessage<?> initiateFlood(ContextView context) {
        return null;
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CiMessage<T> message) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(message);

        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);

        for (CtNode node : neighbors) {
            final FloodPacket<T> stFloodPacket = new FloodPacket<>(context.getTime() + 1, message, sender, node);
            floodPacket(context, stFloodPacket);
        }
    }

    /**
     * Floods the given packet for {@link TransmissionPolicy#getFloodRepeatCount()} times, and
     * also considers {@link BlueFloodSettings#lossProbability()}
     *
     * @param context simulation context
     * @param packet  packet for flooding
     */
    protected void floodPacket(ContextView context, FloodPacket<?> packet) {

        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        for (int i = 0; i < floodRepeatCount; i++)
            context.getSimulator().schedulePacket(packet.buildPacketWithDelayedSchedule(i));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LoyalCtNode node = (LoyalCtNode) o;
        return id == node.id;
    }

    @Override
    public String toString() {
        return "N[" + getId() + "]";
    }
}

