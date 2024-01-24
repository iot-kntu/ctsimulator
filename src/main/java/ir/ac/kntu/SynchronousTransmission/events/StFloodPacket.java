package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
// TODO: 1/24/24 we may cleanup the messageToRcvdNodes map as previous messages are not useful
/**
 * An object that represents a data that should be sent between
 */
public class StFloodPacket<T> extends StEvent {

    private final static HashMap<Integer, HashSet<Node>> messageToRcvdNodes = new HashMap<>();

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

        getLogger().log(Level.INFO, String.format("[%d] node-%d received Pkt[%d]\n",
                                                  getTime(), receiver.getId(), getStMessage().messageNo()));

        if (!messageToRcvdNodes.containsKey(stMessage.messageNo()))
            messageToRcvdNodes.put(stMessage.messageNo(), new HashSet<>());

        final HashSet<Node> messageReceivers = messageToRcvdNodes.get(stMessage.messageNo());

        // check to see if a node previously has received the packet
        //  if no, flood it; otherwise stop flooding
        if (!messageReceivers.contains(receiver)) {
            messageReceivers.add(getReceiver());

            context.onPacketReceive(this, context);

            if (messageReceivers.size() == context.getNetGraph().getNodeCount()) {
                context.getSimulator().roundFinished();
            }
            else {
                context.getSimulator().flood(receiver, stMessage);
            }
        }

    }
}
