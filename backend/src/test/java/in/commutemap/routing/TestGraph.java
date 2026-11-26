package in.commutemap.routing;

import in.commutemap.data.DataLoader;
import in.commutemap.data.TransitData;

import java.nio.file.Path;

/** Loads the committed data once for all tests in the JVM. */
public final class TestGraph {

    private static TransitGraph graph;

    private TestGraph() {}

    public static synchronized TransitGraph get() {
        if (graph == null) {
            TransitData data = DataLoader.load(Path.of("..", "data"));
            graph = new TransitGraph(data);
        }
        return graph;
    }
}
