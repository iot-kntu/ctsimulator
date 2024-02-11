package ir.ac.kntu.SynchronousTransmission;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.logging.Logger;

public class StEvent implements Comparable<StEvent> {

    private final long time;
    private final StEventPriority priority;
    private final Logger logger = Logger.getLogger("StEvent");

    public StEvent(long time) {
        this.time = time;
        this.priority = StEventPriority.Normal;
    }

    public StEvent(long time, StEventPriority priority) {
        this.time = time;
        this.priority = priority;
    }

    public long getTime() {
        return time;
    }

    public StEventPriority getPriority() {
        return priority;
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
    public void handle(ContextView context) {
        Objects.requireNonNull(context);
    }

    @Override
    public int compareTo(StEvent o) {
        if (this.time == o.time)
            // as high priorities has higher value
            //  the reverse of comparison result should be considered
            return o.priority.compareTo(this.priority);
        else
            return (int) (this.time - o.time);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StEvent.class.getSimpleName() + "[", "]")
                .add("t=" + time)
                .toString();
    }
}

