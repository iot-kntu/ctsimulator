package ir.ac.kntu.concurrenttransmission.chaos.state;

public enum ChaosNodeState {
    Sleep('S'),
    Listen('R'),
    Flood('T'),
    FinalFlood('F');

    private final char symbol;

    ChaosNodeState(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }
}
