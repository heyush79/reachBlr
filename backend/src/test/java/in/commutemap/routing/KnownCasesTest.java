package in.commutemap.routing;

import in.commutemap.data.TransitData;
import in.commutemap.routing.Search.Kind;
import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.SearchQuery.Network;
import in.commutemap.routing.SearchQuery.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The checks recorded in reference/.../NOTES.md ("Checks we did"). */
class KnownCasesTest {

    private static final TransitGraph G = TestGraph.get();
    /** Prototype mode (ride fix off), where these numbers were recorded. */
    private static final Router ROUTER = new Router(G, false);
    /** The app's default: the ride fix on. */
    private static final Router FIXED = new Router(G, true);

    private static final double[] ITPL = {12.9855, 77.7368};
    private static final double[] MG_ROAD = {12.9716, 77.5960};

    private static long reachable(double[] office, Network net) {
        return reachable(ROUTER, office, net);
    }

    private static long reachable(Router router, double[] office, Network net) {
        return router.search(new SearchQuery(office[0], office[1], 45, Access.WALK, 15, TimeOfDay.MORNING, net))
                .reachableCount();
    }

    /** The fix can only add wards or make them quicker; on these searches the counts stay the same. */
    @Test
    void sameCountsWithTheRideFix() {
        assertEquals(17, reachable(FIXED, ITPL, Network.METRO));
        assertEquals(22, reachable(FIXED, ITPL, Network.ALL));
        assertEquals(50, reachable(FIXED, MG_ROAD, Network.METRO));
        assertEquals(86, reachable(FIXED, MG_ROAD, Network.ALL));
    }

    @Test
    void itplWithin45MinutesWalking() {
        assertEquals(17, reachable(ITPL, Network.METRO), "metro only");
        assertEquals(22, reachable(ITPL, Network.ALL), "metro and bus");
    }

    @Test
    void mgRoadWithin45MinutesWalking() {
        assertEquals(50, reachable(MG_ROAD, Network.METRO), "metro only");
        assertEquals(86, reachable(MG_ROAD, Network.ALL), "metro and bus");
    }

    /** Timetable: Pattandur Agrahara -> Indiranagar on the Purple Line takes 27 min (weekday 8:35 am). */
    @Test
    void pattandurAgraharaToIndiranagarIs27Minutes() {
        TransitData d = G.data();
        String from = stationId(d, "Pattandur Agrahara"), to = stationId(d, "Indiranagar");
        // add up timetable ride times along the Purple Line
        Map<String, Integer> seconds = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        seconds.put(from, 0);
        queue.add(from);
        while (!queue.isEmpty()) {
            String s = queue.poll();
            for (TransitData.Edge e : d.edges()) {
                if (e.line().equals("PURPLE") && e.from().equals(s) && !seconds.containsKey(e.to())) {
                    seconds.put(e.to(), seconds.get(s) + e.seconds());
                    queue.add(e.to());
                }
            }
        }
        double rideMinutes = seconds.get(to) / 60.0;
        assertEquals(27, rideMinutes, 1.0, "timetable ride");

        // and the search, starting at the station, spends the same time on the train
        TransitData.Station st = d.stations().stream().filter(s -> s.id().equals(from)).findFirst().orElseThrow();
        Search s = ROUTER.search(new SearchQuery(st.lat(), st.lon(), 90, Access.WALK, 15, TimeOfDay.MORNING, Network.METRO));
        int start = s.nb + G.msKey.get(from + "|PURPLE");
        int end = s.nb + G.msKey.get(to + "|PURPLE");
        assertEquals(Kind.START_METRO, s.kindOf(start));
        assertEquals(rideMinutes, s.dist[end] - s.dist[start], 1e-9, "only riding between the two");
        assertEquals(27, Math.round(s.dist[end] - s.dist[start]));
    }

    /** Timetable rides of 6.7-7.5 min x 1.8 for morning traffic: the prototype showed about 13 min. */
    @Test
    void manyathaTechParkToKalyananagaraBusIsAbout13Minutes() {
        TransitData.BusStops b = G.data().busStops();
        int from = -1;
        for (int i = 0; i < b.size(); i++) if (b.name()[i].equals("Manyatha Tech Park")) from = i;
        assertTrue(from >= 0);
        Search s = ROUTER.search(new SearchQuery(b.lat()[from], b.lon()[from], 30, Access.WALK, 5, TimeOfDay.MORNING, Network.ALL));
        double ride = -1;
        for (int i = 0; i < b.size(); i++) {
            if (b.name()[i].equals("Kalyananagara") && s.kindOf(i) == Kind.BUS && s.isBusStop(s.fromOf(i))
                    && b.name()[s.fromOf(i)].equals("Manyatha Tech Park")) {
                ride = s.value2Of(i);
            }
        }
        assertTrue(ride > 0, "a direct bus from Manyatha Tech Park to Kalyananagara");
        assertTrue(ride >= 12 && ride <= 13.5, "ride with traffic, got " + ride);
        assertEquals(13, Math.round(ride));
    }

    private static String stationId(TransitData d, String name) {
        return d.stations().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow().id();
    }
}
