package ir.ac.kntu.SynchronousTransmission;

public interface ReadOnlyContext {

    StSimulator getSimulator();

    StApplication getApplication();

    long getTime();

    int getRound();

    int getSlot();

    NetGraph getNetGraph();

    Node getRoundInitiator();

}
