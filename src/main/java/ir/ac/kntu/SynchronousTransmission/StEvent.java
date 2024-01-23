package ir.ac.kntu.SynchronousTransmission;

import java.util.Objects;
import java.util.logging.Logger;

public class StEvent implements Comparable<StEvent> {

    private final long time;
    private final Logger logger = Logger.getLogger("StEvent");

    public StEvent(long time) {
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Handles the event. Implementation in this class does nothing, thus, should be overriden in
     * subclasses to do meaningful operation.
     *
     * @param context gives the read only state of simulation instance
     */
    public void handle(ReadOnlyContext context) {
        Objects.requireNonNull(context);
    }

    @Override
    public int compareTo(StEvent o) {
        return (int) (this.time - o.time);
    }
}

