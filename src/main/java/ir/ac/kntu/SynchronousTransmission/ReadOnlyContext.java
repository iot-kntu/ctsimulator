package ir.ac.kntu.SynchronousTransmission;

import java.util.List;

public interface ReadOnlyContext extends StApplication {

    StSimulator getSimulator();

    List<StApplication> getApplications();

    long getTime();

    int getRound();

    int getSlot();

    NetGraph getNetGraph();

    Node getRoundInitiator();

}
