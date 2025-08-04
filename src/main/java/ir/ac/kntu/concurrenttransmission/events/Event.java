package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.BaseSimEvent;
import ir.ac.kntu.concurrenttransmission.ContextView;

/**
 * A static helper class (Factory) for creating SimEvent instances
 * using lambda expressions for the handle logic. This makes event
 * creation more concise and readable.
 */
public final class Event {

    // Private constructor to prevent instantiation of this utility class.
    private Event() {
    }

    /**
     * Creates a generic simulation event on the fly.
     *
     * @param name     Event name.
     * @param time     The execution time of the event.
     * @param priority The priority of the event.
     * @param handler  The action to be executed, provided as a lambda expression.
     * @return A new SimEvent instance.
     */
    public static SimEvent create(String name, long time, SimEventPriority priority, EventHandler handler) {
        // We use an anonymous inner class that extends BaseSimEvent
        // to implement the SimEvent interface on the fly.
        return new BaseSimEvent(time, priority) {
            @Override
            public void handle(ContextView context) {
                // The handle method simply calls the lambda expression we passed in.
                handler.handle(context);
            }

            @Override
            public String toString() {
                return String.format("%s[t=%d, p=%s]", name, getTime(), getPriority());
            }
        };
    }

    /**
     * Overloaded helper method for creating an event with Normal priority.
     */
    public static SimEvent create(String name, long time, EventHandler handler) {
        return create(name, time, SimEventPriority.Normal, handler);
    }
}