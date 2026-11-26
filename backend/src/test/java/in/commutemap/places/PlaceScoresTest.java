package in.commutemap.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.commutemap.data.TransitData;
import in.commutemap.data.TransitData.*;
import in.commutemap.routing.TestGraph;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlaceScoresTest {

    /** golden.json holds the scores from the prototype's own buildPlaces(), run on the same data. */
    @Test
    void sameScoresAsThePrototype() throws Exception {
        JsonNode golden;
        try (InputStream in = getClass().getResourceAsStream("/golden/golden.json")) {
            golden = new ObjectMapper().readTree(in).get("scores");
        }
        TransitData d = TestGraph.get().data();
        PlaceScores s = new PlaceScores(d);
        List<String> diffs = new ArrayList<>();
        for (int i = 0; i < d.wards().size(); i++) {
            JsonNode g = golden.get(i);
            int[] sc = s.scores(i);
            for (int k = 0; k < sc.length; k++) {
                if (sc[k] != g.get("sc").get(k).asInt()) diffs.add("ward " + i + " type " + k);
            }
            if (s.overall(i) != g.get("score").asInt()) diffs.add("ward " + i + " overall");
        }
        assertTrue(diffs.isEmpty(), diffs.toString());
    }

    /**
     * Four wards of 1 km² with 0, 2, 2 and 6 gyms. Densities 0, 2, 2, 6.
     * Ward 0 has none: 0. Wards 1 and 2 tie: (1 lower + 2 equal / 2) / 4 = 50. Ward 3: (3 + 1/2) / 4 = 87.5, rounds to 88.
     * A tiny ward counts as 0.2 km²: ward 4 below has 1 gym in 0.05 km² -> density 5, not 20.
     */
    @Test
    void rankFormulaWithTiesAndSmallWards() {
        PlaceScores s = new PlaceScores(world(new double[]{1, 1, 1, 1}, new int[]{0, 2, 2, 6}));
        assertArrayEquals(new int[]{0, 50, 50, 88}, new int[]{s.scores(0)[0], s.scores(1)[0], s.scores(2)[0], s.scores(3)[0]});
        assertEquals(Math.round((0 + 50) / 2.0), s.overall(1), "average of the two types (second type all zero)");

        PlaceScores small = new PlaceScores(world(new double[]{1, 0.05}, new int[]{6, 1}));
        assertEquals(25, small.scores(1)[0], "density 1 / 0.2 = 5 is below 6: (0 + 1/2) / 2 = 25%");
    }

    private static TransitData world(double[] areas, int[] gyms) {
        List<Ward> wards = new ArrayList<>();
        int[][] count = new int[areas.length][];
        int[][] nearest = new int[areas.length][];
        for (int i = 0; i < areas.length; i++) {
            wards.add(new Ward(i, String.valueOf(i + 1), "W" + i, new double[]{12.9, 77.6}, areas[i], List.of(), new int[0], new int[0]));
            count[i] = new int[]{gyms[i], 0};
            nearest[i] = new int[]{0, -1};
        }
        Places places = new Places("test", "2026-01-01", List.of(new Category("gym", "Gyms", "Gym", "a gym"),
                new Category("park", "Parks", "Park", "a park")), count, nearest);
        return new TransitData(new Feeds("t", "t", "t", "t"), List.of(), Map.of(), List.of(), Map.of(), wards, List.of(),
                new BusStops(new double[0], new double[0], new String[0]), List.of(), places);
    }
}
