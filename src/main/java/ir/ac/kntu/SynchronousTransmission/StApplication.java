package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

/**
 * An interface defining applications on the Synch. Transmission base. Applications can be
 * chained.
 */
public interface StApplication {

    default StApplication next() {
        return new NullApplication();
    }

    void addNextApplication(StApplication application);

    /**
     * Called by simulator to announce simulation start
     * @param context
     */
    void simulationStarting(ContextView context);

    void simulationFinishing(ContextView context);

    /**
     * Called when simulation time progresses by one unit
     * @param context
     */
    void simulationTimeProgressed(ContextView context);

    void initiateFlood(ContextView context);

    void packetReceived(StFloodPacket<?> packet, ContextView context);

    void newRound(ContextView context);
}

