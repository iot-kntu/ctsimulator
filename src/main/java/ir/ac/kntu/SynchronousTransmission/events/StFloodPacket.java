package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StMessage;

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

    public StFloodPacket<T> createFor(long time, Node newSender, Node newReceiver) {
        return new StFloodPacket<>(time, stMessage, newSender, newReceiver);
    }
}
