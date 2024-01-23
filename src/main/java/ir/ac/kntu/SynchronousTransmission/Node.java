package ir.ac.kntu.SynchronousTransmission;

import java.util.Objects;
import java.util.logging.Logger;

public class Node {
    public static final Node NULL_NODE = new Node(-1);

    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final int id;


    public Node(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Node node = (Node) o;
        return id == node.id;
    }

    @Override
    public String toString() {
        return "[Node " + getId() + "]";
    }
}
