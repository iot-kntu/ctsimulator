package ir.ac.kntu.SynchronousTransmission;

public class SimpleStringBlueFloodMessageBuilder implements BlueFloodMessageBuilder<String> {
    static int counter = 1;

    @Override
    public CiMessage<String> buildMessage(ContextView context, Node inode) {

        final int currentInitiatorId = inode.getId();
        String msg = "MSG-" + currentInitiatorId + "-" + counter++;
        return new CiMessage<>(inode, msg);
    }
}
