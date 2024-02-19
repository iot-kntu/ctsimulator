package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.blueflood.NodeFloodStrategyType;

import java.util.Objects;

public class Node implements Comparable<Node>{
    public static final Node NULL_NODE = new Node(-1);

    private final int id;
    private NodeFloodStrategyType floodStrategy;


    public Node(int id) {
        this.id = id;
        this.floodStrategy = NodeFloodStrategyType.Normal;
    }

    public int getId() {
        return id;
    }

    public NodeFloodStrategyType getFloodStrategy() {
        return floodStrategy;
    }

    public Node setFloodStrategy(NodeFloodStrategyType floodStrategy) {
        this.floodStrategy = floodStrategy;
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
        return "N[" + getId() + "]";
    }

    @Override
    public int compareTo(Node o) {
        Objects.requireNonNull(o);

        return this.getId() - o.getId();
    }
}
