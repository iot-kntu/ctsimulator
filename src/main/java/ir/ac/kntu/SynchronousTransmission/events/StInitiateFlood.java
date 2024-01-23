package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StEvent;
import ir.ac.kntu.SynchronousTransmission.StMessage;

import java.util.logging.Level;

public class StInitiateFlood extends StEvent {

    public StInitiateFlood(long time) {
        super(time);
    }

    @Override
    public void handle(ReadOnlyContext context) {
        super.handle(context);

        getLogger().log(Level.INFO, "["+context.getTime()+"] Node-" + context.getRoundInitiator().getId() + " initiated a message.");

        final Node node = context.getRoundInitiator();
        final StMessage<?> message = context.getApplication().initiateFlood(context);
        context.getSimulator().flood(node, message);

    }
}
