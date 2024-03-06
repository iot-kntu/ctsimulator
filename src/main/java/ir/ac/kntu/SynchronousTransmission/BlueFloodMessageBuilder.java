package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.LoyalCtNode;

public interface BlueFloodMessageBuilder<T> {
    CiMessage<T> buildMessage(ContextView context, LoyalCtNode inode);
}
