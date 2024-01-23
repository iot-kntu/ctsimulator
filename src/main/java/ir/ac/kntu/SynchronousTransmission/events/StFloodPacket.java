package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.logging.Level;

/**
 * An object that represents a data that should be sent between
 */
public class StFloodPacket<T> extends StEvent {

    private static int PacketNoGen = 0;

    private final StMessage<T> stMessage;
    private final Node sender;
    private final Node receiver;
    private final int packetNo;

    public StFloodPacket(long time, StMessage<T> stMessage, Node sender, Node receiver) {
        super(time);
        this.stMessage = stMessage;
        this.sender = sender;
        this.receiver = receiver;
        this.packetNo = PacketNoGen++;
    }

    public StMessage<T> getStMessage() {
        return stMessage;
    }

    public Node getSender() {
        return sender;
    }

    public Node getReceiver() {
        return receiver;
    }

    public int getPacketNo() {
        return packetNo;
    }

    @Override
    public void handle(ReadOnlyContext context) {
        super.handle(context);

        getLogger().log(Level.INFO, String.format("[%d] node-%d received Pkt[%d]",
                                                  getTime(), receiver.getId(), getPacketNo()));

        context.getApplication().onPacketReceive(this, context);

        context.getSimulator().flood(receiver, stMessage);
    }
}
