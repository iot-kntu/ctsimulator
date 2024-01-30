package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.StringJoiner;
import java.util.logging.Level;

// TODO: 1/24/24 we may cleanup the messageToRcvdNodes map as previous messages are not useful

/**
 * An object that represents a data that should be sent between
 */
public class StFloodPacket<T> extends StEvent {

    private final StMessage<T> stMessage;
    private final Node sender;
    private final Node receiver;

    public StFloodPacket(long time, StMessage<T> stMessage, Node sender, Node receiver) {
        super(time);
        this.stMessage = stMessage;
        this.sender = sender;
        this.receiver = receiver;
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

    @Override
    public void handle(ReadOnlyContext context) {
        super.handle(context);

        getLogger().log(Level.INFO, String.format("[t:%d-r:%d-s:%d] node-%d received Pkt[%d]",
                                                  getTime(), context.getRound(), context.getSlot(),
                                                  receiver.getId(), getStMessage().messageNo()));

        context.getApplication().onPacketReceive(this, context);
    }

    public StEvent scheduleFor(int delay) {
        if (delay < 0)
            throw new IllegalArgumentException("delay must be 0 or a positive number");
        if(delay == 0)
            return this;

        return new StFloodPacket<>(getTime() + delay, getStMessage(), getSender(), getReceiver());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StFloodPacket.class.getSimpleName() + "[", "]")
                .add(getTime() + ":")
                .add(sender + " -> " + receiver)
                .add("msg=" + stMessage)
                .toString();
    }
}
