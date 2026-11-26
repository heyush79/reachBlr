package in.commutemap.routing;

import java.util.List;

/**
 * The result of one search: the fastest time to every graph node, how each node was reached
 * (so a trip can be traced back), and the best time to each ward.
 */
public final class Search {

    /** How a node was reached. */
    public enum Kind { START_METRO, START_BUS, BUS, WALK, ENTER, RIDE, CHANGE, EXIT }

    final SearchQuery query;
    final TransitGraph graph;
    /** Number of bus-stop nodes in this search (0 for metro only); metro states come after them. */
    final int nb;
    /** Nodes per layer (bus stops + metro states). With the ride fix, node v + base is v's "has ridden" copy. */
    final int base;
    final double[] dist;
    final int[] prevFrom;       // -1 for start links
    final Kind[] prevKind;
    final int[] prevPattern, prevI, prevJ, prevRoutes;
    final double[] prevVal;     // START: leg; BUS: wait; WALK/ENTER/EXIT: walk minutes; RIDE/CHANGE: minutes
    final double[] prevVal2;    // START/ENTER: wait; BUS: ride minutes

    private final List<WardResult> wards;
    private final NearestStation nearestStation;
    private final NearestBusStop nearestBusStop;
    private final double millis;

    Search(SearchQuery query, TransitGraph graph, int nb, int base, double[] dist, int[] prevFrom, Kind[] prevKind,
           int[] prevPattern, int[] prevI, int[] prevJ, int[] prevRoutes, double[] prevVal, double[] prevVal2,
           List<WardResult> wards, NearestStation nearestStation, NearestBusStop nearestBusStop, double millis) {
        this.query = query;
        this.graph = graph;
        this.nb = nb;
        this.base = base;
        this.dist = dist;
        this.prevFrom = prevFrom;
        this.prevKind = prevKind;
        this.prevPattern = prevPattern;
        this.prevI = prevI;
        this.prevJ = prevJ;
        this.prevRoutes = prevRoutes;
        this.prevVal = prevVal;
        this.prevVal2 = prevVal2;
        this.wards = wards;
        this.nearestStation = nearestStation;
        this.nearestBusStop = nearestBusStop;
        this.millis = millis;
    }

    public SearchQuery query() {
        return query;
    }

    public TransitGraph graph() {
        return graph;
    }

    /** One entry per ward, in ward order; {@code null} where no trip fits the limits. */
    public List<WardResult> wards() {
        return wards;
    }

    public WardResult ward(int index) {
        return wards.get(index);
    }

    /** Wards within the time limit (after rounding to whole minutes, as the prototype did). */
    public long reachableCount() {
        return wards.stream().filter(w -> w != null && w.minutes() <= query.limit()).count();
    }

    public NearestStation nearestStation() {
        return nearestStation;
    }

    /** {@code null} when buses are off. */
    public NearestBusStop nearestBusStop() {
        return nearestBusStop;
    }

    public double millis() {
        return millis;
    }

    // ---- reading how a node was reached (for tracing a trip back) ----

    public Kind kindOf(int v) {
        return prevKind[v];
    }

    /** The node this one was reached from, or -1 for a start link. */
    public int fromOf(int v) {
        return prevFrom[v];
    }

    /** START: minutes to the stop; BUS: wait; WALK/ENTER/EXIT: walk minutes; RIDE/CHANGE: minutes. */
    public double valueOf(int v) {
        return prevVal[v];
    }

    /** START/ENTER: wait in minutes; BUS: ride minutes. */
    public double value2Of(int v) {
        return prevVal2[v];
    }

    /** BUS: first and last position along the route pattern. */
    public int boardPosition(int v) {
        return prevI[v];
    }

    public int alightPosition(int v) {
        return prevJ[v];
    }

    /** BUS: how many route patterns were counted together. */
    public int routesCounted(int v) {
        return prevRoutes[v];
    }

    public String patternLabel(int v) {
        return graph.patLabel[prevPattern[v]];
    }

    public int[] patternStops(int v) {
        return graph.patStops[prevPattern[v]];
    }

    public boolean isBusStop(int v) {
        return v % base < nb;
    }

    /** Bus stop index of a bus-stop node. */
    public int busStopOf(int v) {
        return v % base;
    }

    /** Station index of a metro node. */
    public int stationOf(int v) {
        return graph.msStation[v % base - nb];
    }

    /** Line id of a metro node. */
    public String lineOf(int v) {
        return graph.msLine[v % base - nb];
    }

    /**
     * Best way to reach a ward.
     *
     * @param exactMinutes time before rounding
     * @param minutes      rounded to whole minutes
     * @param direct       straight from the office, no bus or metro
     * @param byMetro      the last ride was the metro (so the trip ends by leaving a station)
     * @param node         last graph node of the trip (-1 if direct)
     * @param lastLeg      minutes from the last stop into the ward (or the whole trip if direct)
     */
    public record WardResult(int ward, double exactMinutes, int minutes, boolean direct, boolean byMetro,
                             int node, double lastLeg) {}

    public record NearestStation(int station, String name, double minutes, double metres) {}

    /** {@code patternsStopping}: how many route patterns stop there (what the prototype called "routes"). */
    public record NearestBusStop(int stop, String name, double metres, double minutes, int patternsStopping) {}
}
