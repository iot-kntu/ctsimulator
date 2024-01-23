package ir.ac.kntu.SynchronousTransmission;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import java.util.List;
import java.util.function.Supplier;

import static org.jgrapht.util.SupplierUtil.DEFAULT_EDGE_SUPPLIER;

public class NetGraph {

    private final DefaultUndirectedGraph<Node, DefaultEdge> graph;

    public NetGraph() {
        graph = new DefaultUndirectedGraph<>(
                new Supplier<>() {
                    private int idCounter = 0;

                    @Override
                    public Node get() {
                        return new Node(idCounter++);
                    }
                },
                DEFAULT_EDGE_SUPPLIER, false
        );
    }

    public NetGraph(DefaultUndirectedGraph<Node, DefaultEdge> graph) {
        this.graph = graph;
    }

    public int getNodeCount(){
        return graph.vertexSet().size();
    }

    public Node getNodeById(int nodeId) {
        final List<Node> list = graph.vertexSet().stream()
                                     .filter(node -> node.getId() == nodeId)
                                     .distinct().toList();
        if(list.isEmpty())
            return Node.NULL_NODE;

        return list.get(0);
    }

    public List<Node> getNodeNeighbors(Node node){
        return Graphs.neighborListOf(graph, node);
    }
}
