package ir.ac.kntu.SynchronousTransmission;

public enum NodeState {

    Sleep('S'),
    Listen('L'),
    Flood('F');

    private final char symbol;

    NodeState(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }

}
