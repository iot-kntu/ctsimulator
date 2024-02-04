package ir.ac.kntu.SynchronousTransmission.applications;

import ir.ac.kntu.SynchronousTransmission.NullApplication;
import ir.ac.kntu.SynchronousTransmission.ReadOnlyContext;
import ir.ac.kntu.SynchronousTransmission.StApplication;
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

    public void onTimeProgress(ReadOnlyContext context) {
        Objects.requireNonNull(context);
        next().onTimeProgress(context);
    }

    public void onInitiateFlood(ReadOnlyContext context) {
        Objects.requireNonNull(context);

        next().onInitiateFlood(context);
    }

    public void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context) {
        Objects.requireNonNull(packet);
        Objects.requireNonNull(context);

        next().onPacketReceive(packet, context);
    }
}

