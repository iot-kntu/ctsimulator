package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.BlueFloodSettings;
import ir.ac.kntu.SynchronousTransmission.ContextView;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.StMessage;
import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class FaultyFloodStrategy implements FloodStrategy {

    private final Logger logger = Logger.getLogger("NormalFloodStrategy");
    private final Random random = new Random(new Date().getTime());

    protected final BlueFloodSettings settings;

    public FaultyFloodStrategy(BlueFloodSettings settings) {
        this.settings = settings;
    }

    @Override
    public <T> void floodMessage(ContextView context, Node sender, StMessage<T> message) {

        final List<Node> neighbors = context.getNetGraph().getNodeNeighbors(sender);

        for (int i = 0; i < settings.floodRepeatCount(); i++) {

            createFaultyMessage(context, sender, message, i);

            for (Node neighbor : neighbors) {

                final StFloodPacket<T> packet = new StFloodPacket<>(context.getTime() + 1 + i,
                                                                    message, sender, neighbor);

                if (random.nextDouble() >= settings.lossProbability())
                    context.getSimulator().scheduleEvent(packet);
                else
                    logger.log(Level.INFO, "PKT[" + packet + "] lost");
            }

        }
    }

    protected abstract <T> void createFaultyMessage(ContextView context, Node sender, StMessage<T> message, int whichRepeat);

}

