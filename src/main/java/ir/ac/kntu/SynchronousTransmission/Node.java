package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.NodeFaultMode;

import java.util.Objects;
import java.util.logging.Logger;

public class Node {
    public static final Node NULL_NODE = new Node(-1);

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private final int id;
    private NodeFaultMode faultMode;


    public Node(int id) {
        this.id = id;
        this.faultMode = NodeFaultMode.Normal;
    }

    public int getId() {
        return id;
    }

    public NodeFaultMode getFaultMode() {
        return faultMode;
    }

    public Node setFaultMode(NodeFaultMode faultMode) {
        this.faultMode = faultMode;
        return this;
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
