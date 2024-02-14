package ir.ac.kntu.SynchronousTransmission;

public interface StInitiatorStrategy {

    int getCurrentInitiatorId();

    int getNextInitiatorId();
}

class OneInitiator implements StInitiatorStrategy{

    private final int initiatorNodeId;

    public OneInitiator(int initiatorNodeId) {
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

class RoundRobin implements StInitiatorStrategy{

    private final int nodesCount;
    private int currentId = -1;

    public RoundRobin(int nodesCount) {
        this.nodesCount = nodesCount;
    }

    @Override
    public int getCurrentInitiatorId() {
        return this.currentId;
    }

    @Override
    public int getNextInitiatorId() {
        int nextId = (this.currentId + 1) % nodesCount;
        int turn = this.currentId;
        this.currentId = nextId;

        return turn;
    }
}
