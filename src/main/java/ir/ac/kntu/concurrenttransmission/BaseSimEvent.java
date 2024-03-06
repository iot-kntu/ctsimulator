package ir.ac.kntu.concurrenttransmission;

import ir.ac.kntu.concurrenttransmission.events.SimEvent;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;

import java.util.StringJoiner;
import java.util.logging.Logger;

public abstract class BaseSimEvent implements SimEvent, Comparable<BaseSimEvent> {

    private final long time;
    private final SimEventPriority priority;
    private final Logger logger = Logger.getLogger("StEvent");

    public BaseSimEvent(long time) {
        this.time = time;
        this.priority = SimEventPriority.Normal;
    }

    public BaseSimEvent(long time, SimEventPriority priority) {
        this.time = time;
        this.priority = priority;
    }

    public long getTime() {
        return time;
    }

    public SimEventPriority getPriority() {
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
    public abstract void handle(ContextView context);

    @Override
    public int compareTo(BaseSimEvent o) {
        if (this.time == o.time)
            // as high priorities has higher value
            //  the reverse of comparison result should be considered
            return o.priority.compareTo(this.priority);
        else
            return (int) (this.time - o.time);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", BaseSimEvent.class.getSimpleName() + "[", "]")
                .add("t=" + time)
                .toString();
    }
}

