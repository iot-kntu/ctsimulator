package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.LoyalCtNode;

public class SimpleStringBlueFloodMessageBuilder implements BlueFloodMessageBuilder<String> {
    static int counter = 1;

    @Override
    public CiMessage<String> buildMessage(ContextView context, LoyalCtNode inode) {

        final int currentInitiatorId = inode.getId();
        String msg = "MSG-" + currentInitiatorId + "-" + counter++;
        return new CiMessage<>(inode, msg);
    }
}
