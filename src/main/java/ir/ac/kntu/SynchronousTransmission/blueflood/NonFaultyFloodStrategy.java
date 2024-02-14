package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;
import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NonFaultyFloodStrategy implements FloodStrategy {

    private final Logger logger = Logger.getLogger("NormalFloodStrategy");
    private final Random random = new Random(new Date().getTime());
    
    private final BlueFloodSettings settings;

    public NonFaultyFloodStrategy(BlueFloodSettings settings) {
        this.settings = settings;
    }

    @Override
    public <T> void floodMessage(ContextView context, Node sender, StMessage<T> message) {

        final List<Node> neighbors = context.getNetGraph().getNodeNeighbors(sender);

        for (Node node : neighbors) {
            final StFloodPacket<T> stFloodPacket = new StFloodPacket<>(context.getTime() + 1, message, sender, node);
            floodPacket(context, stFloodPacket);
        }
    }

    /**
     * Floods the given packet for {@link BlueFloodSettings#floodRepeatCount()} times, and
     * also considers {@link BlueFloodSettings#lossProbability()}
     *
     * @param context simulation context
     * @param packet  packet for flooding
     */
    protected void floodPacket(ContextView context, StFloodPacket<?> packet) {

        for (int i = 0; i < settings.floodRepeatCount(); i++) {

            StEvent event = packet.scheduleWithDelay(i);

            if (random.nextDouble() >= settings.lossProbability())
                context.getSimulator().scheduleEvent(event);
            else
                logger.log(Level.INFO, "PKT[" + packet + "] lost");
        }
    }

}
