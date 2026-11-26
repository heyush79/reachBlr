package in.commutemap.routing;

import in.commutemap.data.TransitData;
import in.commutemap.data.TransitData.*;

import java.util.List;
import java.util.Map;

/**
 * A tiny made-up network where every answer can be worked out by hand.
 *
 * <pre>
 *   bus stops (all on latitude 12.95):  X 77.60 -- Y 77.62 -- Z 77.64 -- Q 77.66
 *   routes X->Y:  A 10 min every 10 min, B 11 min every 10 min, C 20 min every 5 min
 *   route  Y->Z:  E 5 min every 0.5 min (morning only)
 *   route  X->Q:  D 15 min every 60 min (morning only)
 *   metro line TEST: S1 (12.99, 77.60) -- 10 min -- S2 (12.99, 77.65); trains every 5 min at S1, no data at S2
 *   wards: 0 around Y, 1 north of X (1.1 km), 2 next door to X (440 m), 3 around S2
 * </pre>
 */
final class TinyNetwork {

    static final int X = 0, Y = 1, Z = 2, Q = 3;
    static final int WARD_AT_Y = 0, WARD_NORTH_OF_X = 1, WARD_NEXT_DOOR = 2, WARD_AT_S2 = 3;

    private TinyNetwork() {}

    static TransitData build() {
        List<Station> stations = List.of(
                new Station("S1", "S1", 12.99, 77.60, List.of("TEST")),
                new Station("S2", "S2", 12.99, 77.65, List.of("TEST")));
        Map<String, Line> lines = Map.of("TEST", new Line("TEST", "Test", "#123456", "Test line",
                List.of(new double[]{12.99, 77.60}, new double[]{12.99, 77.65})));
        List<Edge> edges = List.of(new Edge("TEST", "S1", "S2", 600), new Edge("TEST", "S2", "S1", 600));
        Map<String, Map<String, int[]>> headways = Map.of("S1", Map.of("TEST", new int[]{300, 300}));
        List<Ward> wards = List.of(
                ward(0, "At Y", 12.95, 77.62, 0.003, new int[0], new int[0]),
                ward(1, "North of X", 12.96, 77.60, 0.003, new int[0], new int[0]),
                ward(2, "Next door", 12.954, 77.60, 0.001, new int[0], new int[0]),
                ward(3, "At S2", 12.99, 77.65, 0.003, new int[]{1}, new int[]{0}));
        BusStops stops = new BusStops(new double[]{12.95, 12.95, 12.95, 12.95},
                new double[]{77.60, 77.62, 77.64, 77.66}, new String[]{"X", "Y", "Z", "Q"});
        List<Pattern> patterns = List.of(
                new Pattern("A", List.of("A"), 10, 10, new int[]{X, Y}, new int[]{0, 100}),
                new Pattern("B", List.of("B"), 10, 10, new int[]{X, Y}, new int[]{0, 110}),
                new Pattern("C", List.of("C"), 5, 5, new int[]{X, Y}, new int[]{0, 200}),
                new Pattern("D", List.of("D"), 60, 0, new int[]{X, Q}, new int[]{0, 150}),
                new Pattern("E", List.of("E"), 0.5, 0, new int[]{Y, Z}, new int[]{0, 50}));
        return new TransitData(new Feeds("test", "test", "test", null), stations, lines, edges, headways, wards,
                List.of(), stops, patterns, null);
    }

    private static Ward ward(int index, String name, double lat, double lon, double half, int[] st, int[] m) {
        double[][] ring = {
                {lat - half, lon - half}, {lat - half, lon + half}, {lat + half, lon + half},
                {lat + half, lon - half}, {lat - half, lon - half}};
        // List.<double[][]>of: without the type, List.of would spread the ring into separate points
        return new Ward(index, String.valueOf(index + 1), name, new double[]{lat, lon}, 1.0,
                List.<double[][]>of(ring), st, m);
    }
}
