package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.StNetworkTime;

public interface BlueFloodTransmissionPolicy {

    int getFloodRepeatCount();

    StNetworkTime getNetworkTime(long time);

    void newRoundStarted(Node inode);
}
