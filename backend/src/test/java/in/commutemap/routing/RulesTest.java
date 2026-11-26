package in.commutemap.routing;

import in.commutemap.routing.Search.Kind;
import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.SearchQuery.Network;
import in.commutemap.routing.SearchQuery.TimeOfDay;
import in.commutemap.trip.Trip;
import in.commutemap.trip.TripBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static in.commutemap.routing.TinyNetwork.*;
import static org.junit.jupiter.api.Assertions.*;

/** Each travel-time rule from NOTES.md, on the {@link TinyNetwork} where answers are worked out by hand. */
class RulesTest {

    private static final double EPS = 1e-9;
    private static final TransitGraph G = new TransitGraph(TinyNetwork.build());
    // The rules are the same with or without the ride fix. Prototype mode keeps one node per stop,
    // so the hand-worked node numbers (s.dist[Y] and so on) apply directly.
    private static final Router ROUTER = new Router(G, false);

    private static Search fromX(int limit, TimeOfDay when) {
        return ROUTER.search(new SearchQuery(12.95, 77.60, limit, Access.WALK, 15, when, Network.ALL));
    }

    @Test
    void walkAndAutoTimes() {
        assertEquals(16.25, Geo.walkMin(1000), EPS, "4.8 km/h on a path 30% longer");
        assertEquals(5 + 1000 * 1.4 / 300, Geo.autoMin(1000), EPS, "5 min to get one, 18 km/h, road 40% longer");
    }

    @Test
    void nearlyAsFastRoutesShareTheWait() {
        Search s = fromX(60, TimeOfDay.MORNING);
        // fastest ride 10 min x 1.8 = 18. B (19.8) is within 1.25 x 18 + 2 = 24.5; C (36) is not.
        // A and B together run every 5 min: wait 2.5. Ride = their average weighted by frequency.
        assertEquals(Kind.BUS, s.kindOf(Y));
        assertEquals(2, s.routesCounted(Y));
        assertEquals(2.5, s.valueOf(Y), EPS, "wait");
        assertEquals((18 + 19.8) / 2, s.value2Of(Y), EPS, "ride");
        assertEquals(2.5 + 18.9, s.dist[Y], EPS);
        assertEquals("A", s.patternLabel(Y), "shown route: the most frequent counted one (first on a tie)");
    }

    @Test
    void eveningUsesTheEveningTrafficFactor() {
        Search s = fromX(60, TimeOfDay.EVENING);
        assertEquals(2.5 + (14 + 15.4) / 2, s.dist[Y], EPS, "rides x 1.4 in the evening");
        assertEquals(Double.POSITIVE_INFINITY, s.dist[Q], "route D has no evening buses");
    }

    @Test
    void waitIsAtMost15Minutes() {
        Search s = fromX(60, TimeOfDay.MORNING);
        assertEquals(15, s.valueOf(Q), EPS, "every 60 min would mean a 30 min wait; capped at 15");
        assertEquals(15 + 15 * 1.8, s.dist[Q], EPS);
    }

    @Test
    void waitIsAtLeast1Minute() {
        Search s = fromX(60, TimeOfDay.MORNING);
        assertEquals(Y, s.fromOf(Z));
        assertEquals(1, s.valueOf(Z), EPS, "every 0.5 min would mean a 0.25 min wait; at least 1");
        assertEquals(s.dist[Y] + 1 + 5 * 1.8, s.dist[Z], EPS);
    }

    @Test
    void wardIsReachedAtItsEdgePlus300Metres() {
        Search s = fromX(60, TimeOfDay.MORNING);
        Search.WardResult r = s.ward(WARD_AT_Y);
        assertEquals(s.dist[Y] + Geo.walkMin(300), r.exactMinutes(), EPS, "stop Y is inside the ward");
        assertEquals(26, r.minutes());
        assertFalse(r.direct());
    }

    @Test
    void walkingToAStopAndAwayAgainDoesNotCount() {
        // stop X is 774 m from this ward, but reaching X involved no ride, and the ward centre is an 18 min walk
        assertNull(fromX(60, TimeOfDay.MORNING).ward(WARD_NORTH_OF_X));
    }

    @Test
    void aStraightWalkFromTheOfficeCountsIfItFits() {
        Search.WardResult r = fromX(60, TimeOfDay.MORNING).ward(WARD_NEXT_DOOR);
        assertTrue(r.direct());
        assertEquals(Math.round(Geo.walkMin(Geo.metres(12.95, 77.60, 12.954, 77.60))), r.minutes());
    }

    @Test
    void nothingBeyondTheTimeLimit() {
        Search s = fromX(20, TimeOfDay.MORNING);
        assertEquals(Double.POSITIVE_INFINITY, s.dist[Y], "21.4 min is over the 20 min limit");
        assertNull(s.ward(WARD_AT_Y));
    }

    @Test
    void metroEntryWaitRideAndExit() {
        Search s = ROUTER.search(new SearchQuery(12.99, 77.60, 60, Access.WALK, 15, TimeOfDay.MORNING, Network.METRO));
        int s2 = s.nb + G.msKey.get("S2|TEST");
        assertEquals(2 + 300 / 120.0 + 10, s.dist[s2], EPS, "enter 2 + half of the 5 min gap + 10 min ride");
        Search.WardResult r = s.ward(WARD_AT_S2);
        assertTrue(r.byMetro());
        assertEquals(s.dist[s2] + 1 + Geo.walkMin(300), r.exactMinutes(), EPS, "leave 1 + walk into the ward");
    }

    @Test
    void stationWithoutGapDataWaitsFiveMinutes() {
        assertEquals(5, G.waitMin[G.msKey.get("S2|TEST")][0], EPS);
    }

    @Test
    void tripIsWrittenStepByStep() {
        Trip t = TripBuilder.build(fromX(60, TimeOfDay.MORNING), WARD_AT_Y);
        assertEquals(List.of(
                "Walk 0 min to X bus stop",
                "Wait about 3 min, then bus A or 1 other route to Y bus stop, 1 stop, about 19 min",
                "Walk 5 min into the nearest part of the ward"), t.steps().stream().map(Trip.Step::text).toList());
        assertEquals(List.of("Bus A"), t.via());
        assertEquals(2, t.steps().get(1).path().size(), "bus line drawn from X to Y");
    }
}
