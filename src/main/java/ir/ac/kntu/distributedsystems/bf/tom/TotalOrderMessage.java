package ir.ac.kntu.distributedsystems.bf.tom;

/**
 * Represents a totally ordered message (or a NOOP placeholder) identified by a sequence number.
 */
public record TotalOrderMessage(int sequence, Object value, boolean noop) {

    public static TotalOrderMessage data(int sequence, Object value) {
        return new TotalOrderMessage(sequence, value, false);
    }

    public static TotalOrderMessage noop(int sequence) {
        return new TotalOrderMessage(sequence, null, true);
    }

    public boolean isNoop() {
        return noop || value == null;
    }
}
