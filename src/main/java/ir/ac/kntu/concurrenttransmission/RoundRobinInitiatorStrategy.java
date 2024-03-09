package ir.ac.kntu.concurrenttransmission;

public class RoundRobinInitiatorStrategy implements CiInitiatorStrategy {

    private final int nodesCount;
    private int currentId = -1;

    public RoundRobinInitiatorStrategy(int nodesCount) {
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
