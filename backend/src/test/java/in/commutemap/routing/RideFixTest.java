package in.commutemap.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.commutemap.data.TransitData;
import in.commutemap.data.TransitData.*;
import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.SearchQuery.Network;
import in.commutemap.routing.SearchQuery.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The optional ride fix: it finds wards the prototype misses, and never makes any ward slower. */
class RideFixTest {

    /**
     * Office (12.95, 77.6990). Stop P is 109 m east, stop R 304 m east. Bus G runs P -> R.
     * Walking to R (4.9 min) beats walking to P and riding (6.4 min), so the prototype keeps only the
     * walk-only way to R, and the ward north of R (reachable only from R, and with a ride) is lost.
     */
    @Test
    void wardBehindAWalkOnlyStopIsFound() {
        TransitData d = new TransitData(new Feeds("t", "t", "t", null),
                List.of(new Station("S", "Far station", 13.10, 77.50, List.of("L"))),
                Map.of("L", new Line("L", "L", "#000", "L", List.of())), List.of(), Map.of(),
                List.of(new Ward(0, "1", "North of R", new double[]{12.9585, 77.7018}, 1.0,
                        List.<double[][]>of(new double[][]{{12.9555, 77.6988}, {12.9555, 77.7048}, {12.9615, 77.7048},
                                {12.9615, 77.6988}, {12.9555, 77.6988}}), new int[0], new int[0])),
                List.of(), new BusStops(new double[]{12.95, 12.95}, new double[]{77.70, 77.7018}, new String[]{"P", "R"}),
                List.of(new Pattern("G", List.of("G"), 2, 2, new int[]{0, 1}, new int[]{0, 20})), null);
        TransitGraph g = new TransitGraph(d);
        SearchQuery q = new SearchQuery(12.95, 77.6990, 45, Access.WALK, 15, TimeOfDay.MORNING, Network.ALL);

        assertNull(new Router(g, false).search(q).ward(0), "prototype: missed");
        Search fixed = new Router(g, true).search(q);
        Search.WardResult r = fixed.ward(0);
        assertNotNull(r, "with the fix: found");
        assertEquals(21, r.minutes(), "walk to P, bus to R, walk into the ward");
        assertEquals(List.of("Bus G"), in.commutemap.trip.TripBuilder.via(fixed, 0));
    }

    @Test
    void neverSlowerOnTheRealData() throws Exception {
        JsonNode doc;
        try (InputStream in = getClass().getResourceAsStream("/golden/golden.json")) {
            doc = new ObjectMapper().readTree(in);
        }
        TransitGraph g = TestGraph.get();
        Router copy = new Router(g, false), fixed = new Router(g, true);
        List<String> report = new ArrayList<>();
        for (JsonNode j : doc.get("queries")) {
            SearchQuery q = new SearchQuery(j.get("office").get(0).asDouble(), j.get("office").get(1).asDouble(),
                    j.get("limit").asInt(), j.get("mode").asText().equals("walk") ? Access.WALK : Access.AUTO,
                    j.get("leg").asInt(), j.get("when").asInt() == 0 ? TimeOfDay.MORNING : TimeOfDay.EVENING,
                    j.get("net").asText().equals("all") ? Network.ALL : Network.METRO);
            Search a = copy.search(q), b = fixed.search(q);
            List<String> changes = new ArrayList<>();
            for (int w = 0; w < g.data().wards().size(); w++) {
                Search.WardResult ra = a.ward(w), rb = b.ward(w);
                if (ra != null) {
                    assertNotNull(rb, j.get("id").asText() + ": ward " + w + " lost with the fix");
                    assertTrue(rb.exactMinutes() <= ra.exactMinutes() + 1e-9, j.get("id").asText() + ": ward " + w + " slower");
                }
                if (rb != null && (ra == null || rb.minutes() != ra.minutes())) {
                    changes.add(g.data().wards().get(w).name() + " " + (ra == null ? "-" : ra.minutes()) + "->" + rb.minutes());
                }
            }
            report.add(String.format("%-42s within limit %3d -> %3d   %s", j.get("id").asText(),
                    a.reachableCount(), b.reachableCount(), changes.isEmpty() ? "no change" : String.join(", ", changes)));
        }
        System.out.println("Ride fix, per golden query (minutes before -> after):\n  " + String.join("\n  ", report));
    }
}
