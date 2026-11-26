package in.commutemap.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.SearchQuery.Network;
import in.commutemap.routing.SearchQuery.TimeOfDay;
import in.commutemap.trip.Trip;
import in.commutemap.trip.TripBuilder;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Java search must give the same answers as the prototype's own JavaScript.
 *
 * <p>golden.json is written by tools/golden/golden.mjs, which runs the prototype's {@code route()} and
 * {@code steps()} unchanged on the same data. For every query and every one of the 243 wards this test
 * compares the minutes, the lines used and the wording of every step.
 */
class GoldenParityTest {

    private static final Router ROUTER = new Router(TestGraph.get());

    @TestFactory
    Stream<DynamicTest> sameAnswersAsThePrototype() throws Exception {
        JsonNode doc;
        try (InputStream in = getClass().getResourceAsStream("/golden/golden.json")) {
            assertNotNull(in, "golden.json missing: run node tools/golden/golden.mjs");
            doc = new ObjectMapper().readTree(in);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode q : doc.get("queries")) {
            tests.add(DynamicTest.dynamicTest(q.get("id").asText(), () -> check(q)));
        }
        return tests.stream();
    }

    private void check(JsonNode g) {
        SearchQuery q = new SearchQuery(g.get("office").get(0).asDouble(), g.get("office").get(1).asDouble(),
                g.get("limit").asInt(), g.get("mode").asText().equals("walk") ? Access.WALK : Access.AUTO,
                g.get("leg").asInt(), g.get("when").asInt() == 0 ? TimeOfDay.MORNING : TimeOfDay.EVENING,
                g.get("net").asText().equals("all") ? Network.ALL : Network.METRO);
        Search s = ROUTER.search(q);

        assertEquals(g.get("reachable").asLong(), s.reachableCount(), "wards within the limit");
        assertEquals(g.get("nearestStation").get("name").asText(), s.nearestStation().name());
        assertEquals(g.get("nearestStation").get("minutes").asDouble(), s.nearestStation().minutes(), 0.0);
        if (g.get("nearestBusStop").isNull()) {
            assertNull(s.nearestBusStop());
        } else {
            JsonNode nb = g.get("nearestBusStop");
            assertEquals(nb.get("name").asText(), s.nearestBusStop().name());
            assertEquals(nb.get("metres").asDouble(), s.nearestBusStop().metres(), 0.0);
            assertEquals(nb.get("patterns").asInt(), s.nearestBusStop().patternsStopping());
        }

        JsonNode wards = g.get("wards");
        assertEquals(wards.size(), s.wards().size());
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < wards.size(); i++) {
            JsonNode expected = wards.get(i);
            Search.WardResult got = s.ward(i);
            if (expected.isNull() || got == null) {
                if (expected.isNull() != (got == null)) {
                    problems.add("ward " + i + ": prototype " + (expected.isNull() ? "unreachable" : expected.get("t"))
                            + ", Java " + (got == null ? "unreachable" : got.minutes()));
                }
                continue;
            }
            if (expected.get("t").asInt() != got.minutes() || expected.get("direct").asBoolean() != got.direct()) {
                problems.add("ward " + i + ": prototype " + expected.get("t") + " min, Java " + got.minutes() + " min");
                continue;
            }
            Trip trip = TripBuilder.build(s, i);
            List<String> via = new ArrayList<>();
            expected.get("via").forEach(n -> via.add(n.asText()));
            if (!via.equals(trip.via())) problems.add("ward " + i + ": lines " + via + " vs " + trip.via());
            List<String> steps = new ArrayList<>();
            expected.get("steps").forEach(n -> steps.add(unescape(n.asText())));
            List<String> ours = trip.steps().stream().map(Trip.Step::text).toList();
            if (!steps.equals(ours)) problems.add("ward " + i + ": steps\n  prototype " + steps + "\n  Java      " + ours);
        }
        assertTrue(problems.isEmpty(), problems.size() + " differences:\n" + String.join("\n", problems));
    }

    /** The prototype HTML-escaped names; the golden file keeps that escaping. */
    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }
}
