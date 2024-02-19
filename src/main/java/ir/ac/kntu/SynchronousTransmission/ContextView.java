package ir.ac.kntu.SynchronousTransmission;

public interface ContextView {

    CtSimulator getSimulator();

    ConcurrentTransmissionApplication getApplication();

    long getTime();

    NetGraph getNetGraph();

    //int getRound();
    //
    //int getSlot();


}
