package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.StringJoiner;

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
    public void handle(ContextView context) {
        super.handle(context);

        context.getApplication().packetReceived(this, context);
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

