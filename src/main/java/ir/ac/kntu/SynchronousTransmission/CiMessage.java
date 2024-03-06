package ir.ac.kntu.SynchronousTransmission;

import java.util.Objects;

/**
 * A non-mutable Constructive Interference message generated by the initiator and can not be modified
 * within a round
 */
public final class CiMessage<T> {

    public static CiMessage NULL_MESSAGE = new CiMessage<>(CtNode.NULL_NODE, 0, null);

    private static int MessageNoGen = 0;

    private final CtNode initiator;
    private final int messageNo;
    private final T content;

    /**
     * @param initiator
     * @param content
     */
    public CiMessage(CtNode initiator, T content) {
        this.initiator = initiator;
        this.messageNo = ++MessageNoGen;
        this.content = content;
    }

    private CiMessage(CtNode initiator, int messageNo, T content) {
        this.initiator = initiator;
        this.messageNo = messageNo;
        this.content = content;
    }

    public boolean isNull() {
        return messageNo == 0 || initiator.equals(CtNode.NULL_NODE);
    }

    public CtNode initiator() {
        return initiator;
    }

    public int messageNo() {
        return messageNo;
    }

    public T content() {
        return content;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initiator, messageNo, content);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (CiMessage) obj;
        return Objects.equals(this.initiator, that.initiator) &&
                this.messageNo == that.messageNo &&
                Objects.equals(this.content, that.content);
    }

    @Override
    public String toString() {
        return "StMessage[I=%s, Msg[%d]=%s]".formatted(initiator, messageNo, content);
    }

}

