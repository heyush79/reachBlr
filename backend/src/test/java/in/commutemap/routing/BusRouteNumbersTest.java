package in.commutemap.routing;

import in.commutemap.data.TransitData;
import in.commutemap.data.TransitData.*;
import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.SearchQuery.Network;
import in.commutemap.routing.SearchQuery.TimeOfDay;
import in.commutemap.trip.Trip;
import in.commutemap.trip.TripBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "or N other routes" counts route numbers, not route variants.
 *
 * <pre>
 *   stops X (12.95, 77.60) -> Y (12.95, 77.62), a ward around Y
 *   variant P1: routes "500-D" and "500-D X" (one route number), 10 min, every 10 min
 *   variant P2: route  "500-D Y" (the same number again),        10 min, every 10 min
 *   variant P3: route  "335-E",                                  11 min, every 20 min
 *   variant P4: route  "999",                                    30 min: too slow to count together
 * </pre>
 * The prototype said "or 2 other routes" (three variants counted, one shown). There are really two route
 * numbers, 500-D (shown) and 335-E, so: "or 1 other route".
 */
class BusRouteNumbersTest {

    private static final int X = 0, Y = 1;

    @Test
    void countsRouteNumbersNotVariants() {
        TransitGraph g = new TransitGraph(network());
        for (boolean rideFix : new boolean[]{false, true}) {
            Search s = new Router(g, rideFix).search(
                    new SearchQuery(12.95, 77.60, 60, Access.WALK, 15, TimeOfDay.MORNING, Network.ALL));
            Trip trip = TripBuilder.build(s, 0);
            Trip.Step bus = trip.steps().stream().filter(st -> st.type() == Trip.StepType.BUS).findFirst().orElseThrow();
            assertEquals("500-D", bus.bus().label());
            assertEquals(List.of("335-E"), bus.bus().otherRouteNumbers());
            assertEquals(1, bus.bus().otherRoutes());
            assertTrue(bus.text().startsWith("Wait about 2 min, then bus 500-D or 1 other route to Y bus stop"), bus.text());
        }
    }

    @Test
    void nearestStopCountsRouteNumbers() {
        Search s = new Router(new TransitGraph(network()), true).search(
                new SearchQuery(12.95, 77.60, 60, Access.WALK, 15, TimeOfDay.MORNING, Network.ALL));
        assertEquals("X", s.nearestBusStop().name());
        assertEquals(3, s.nearestBusStop().routeNumbers(), "500-D, 335-E and 999");
        assertEquals(4, s.nearestBusStop().patternsStopping(), "what the prototype counted");
    }

    @Test
    void routeNumberIsTheFirstWordOfTheName() {
        assertArrayEquals(new String[]{"KBS-1I", "500-D"},
                TransitGraph.routeNumbers(List.of("KBS-1I BEMLF-KDG", " KBS-1I", "500-D", "")));
    }

    private static TransitData network() {
        double[][] ring = {{12.947, 77.617}, {12.947, 77.623}, {12.953, 77.623}, {12.953, 77.617}, {12.947, 77.617}};
        return new TransitData(new Feeds("t", "t", "t", null),
                List.of(new Station("S", "Far station", 13.10, 77.50, List.of("L"))),
                Map.of("L", new Line("L", "L", "#000", "L", List.of())), List.of(), Map.of(),
                List.of(new Ward(0, "1", "At Y", new double[]{12.95, 77.62}, 1.0, List.<double[][]>of(ring), new int[0], new int[0])),
                List.of(),
                new BusStops(new double[]{12.95, 12.95}, new double[]{77.60, 77.62}, new String[]{"X", "Y"}),
                List.of(
                        new Pattern("500-D", List.of("500-D", "500-D X"), 10, 10, new int[]{X, Y}, new int[]{0, 100}),
                        new Pattern("500-D", List.of("500-D Y"), 10, 10, new int[]{X, Y}, new int[]{0, 100}),
                        new Pattern("335-E", List.of("335-E"), 20, 20, new int[]{X, Y}, new int[]{0, 110}),
                        new Pattern("999", List.of("999"), 10, 10, new int[]{X, Y}, new int[]{0, 300})),
                null);
    }
}
