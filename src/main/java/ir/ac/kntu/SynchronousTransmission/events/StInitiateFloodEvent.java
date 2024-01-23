package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.logging.Level;

public class StInitiateFloodEvent extends StEvent {

    public StInitiateFloodEvent(long time) {
        super(time);
    }

    @Override
    public void handle(ReadOnlyContext context) {
        super.handle(context);


        final Node node = context.getRoundInitiator();

        final StMessage<?> message = context.initiateFlood(context);
        getLogger().log(Level.INFO, "[" + context.getTime() + "] Node-" +
                context.getRoundInitiator().getId() +
                " initiated message [" + message.messageNo() + "]");

        context.getSimulator().flood(node, message);

    }
}
