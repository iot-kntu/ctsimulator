package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * This class is the default implementation of {@link StApplication} interface
 * and does nothing.
 */
public abstract class BaseApplication implements StApplication {

    protected StApplication next = null;
    protected Logger logger = Logger.getLogger("applications");

    public StApplication next() {
        return (next == null) ? new NullApplication() : next;
    }

    @Override
    public void addNextApplication(StApplication application) {
        Objects.requireNonNull(application);

        next = application;
    }

    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void simulationStarting(ContextView context) {
        next().simulationStarting(context);
    }

    @Override
    public void simulationFinishing(ContextView context) {
        next().simulationFinishing(context);
    }

    public void simulationTimeProgressed(ContextView context) {
        next().simulationTimeProgressed(context);
    }

    public void initiateFlood(ContextView context) throws RuntimeException {
        next().initiateFlood(context);
    }

    public void packetReceived(StFloodPacket<?> packet, ContextView context) {
        next().packetReceived(packet, context);
    }
}

