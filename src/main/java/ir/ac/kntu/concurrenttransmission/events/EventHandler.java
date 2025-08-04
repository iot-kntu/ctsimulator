package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.ContextView;

/**
 * A functional interface representing the action to be executed by a simulation event.
 * This allows passing the event's logic as a lambda expression.
 */
@FunctionalInterface
public interface EventHandler {
    /**
     * Defines the action to be performed.
     *
     * @param context The current simulation context.
     */
    void handle(ContextView context);
}