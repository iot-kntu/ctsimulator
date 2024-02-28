package ir.ac.kntu.SynchronousTransmission;

public interface BlueFloodMessageBuilder<T> {
    CiMessage<T> buildMessage(ContextView context, Node inode);
}
