package ir.ac.kntu.SynchronousTransmission.events;

import ir.ac.kntu.SynchronousTransmission.ContextView;

public interface SimEvent {

    long getTime();

    SimEventPriority getPriority();

    void handle(ContextView context);
}
