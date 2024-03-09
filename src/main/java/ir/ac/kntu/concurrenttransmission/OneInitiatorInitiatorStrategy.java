package ir.ac.kntu.concurrenttransmission;

public class OneInitiatorInitiatorStrategy implements CiInitiatorStrategy {

    private final int initiatorNodeId;

    public OneInitiatorInitiatorStrategy(int initiatorNodeId) {
        this.initiatorNodeId = initiatorNodeId;
    }

    @Override
    public int getCurrentInitiatorId() {
        return initiatorNodeId;
    }

    @Override
    public int getNextInitiatorId() {
        return this.initiatorNodeId;
    }


}
