package ir.ac.kntu.SynchronousTransmission;

public interface ContextView {

    StSimulator getSimulator();

    StApplication getApplication();

    long getTime();

    NetGraph getNetGraph();

    //int getRound();
    //
    //int getSlot();


}
