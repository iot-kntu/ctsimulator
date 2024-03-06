package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.ContextView;

public interface SimEvent {

    long getTime();

    SimEventPriority getPriority();

    void handle(ContextView context);
}
