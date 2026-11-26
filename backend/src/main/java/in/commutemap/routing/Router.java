package in.commutemap.routing;

import in.commutemap.data.TransitData.Station;
import in.commutemap.data.TransitData.Ward;
import in.commutemap.routing.Search.Kind;
import in.commutemap.routing.Search.NearestBusStop;
import in.commutemap.routing.Search.NearestStation;
import in.commutemap.routing.Search.WardResult;
import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.TransitGraph.MetroArc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Finds the fastest trip from an office to every ward by metro and bus.
 *
 * <p>It is Dijkstra's shortest-path search: always continue from the closest node not yet finished, so
 * every node is finished at its best time. It stops at the time limit. Nodes are bus stops and metro
 * (station, line) states. From a bus stop, all routes to the same next stop are counted together for
 * the wait, as long as they are nearly as fast as the fastest one (at most 1.25 x its ride + 2 min).
 *
 * <p>A line-by-line port of {@code route()} in the prototype (reference/.../prototype/index.html):
 * the golden test checks that both give the same minutes for every ward.
 *
 * <p><b>Ride fix</b> (off by default, to match the prototype). A trip only counts for a ward if it
 * includes a bus or metro ride. The prototype keeps one best time per stop, so if a stop is reached
 * fastest by walking alone, a slightly slower way there that includes a ride is lost, and wards that
 * need it can be missed. With the fix, each node exists twice, "no ride yet" and "has ridden", so both
 * are kept.
 */
public final class Router {

    private final TransitGraph g;
    private final boolean rideFix;

    public Router(TransitGraph graph) {
        this(graph, false);
    }

    public Router(TransitGraph graph, boolean rideFix) {
        this.g = graph;
        this.rideFix = rideFix;
    }

    public boolean rideFix() {
        return rideFix;
    }

    public Search search(SearchQuery q) {
        long t0 = System.nanoTime();
        Run run = new Run(g, q, rideFix);
        run.start();
        run.loop();
        List<WardResult> wards = run.wards();
        double ms = (System.nanoTime() - t0) / 1e6;
        return new Search(q, g, run.nb, run.base, run.dist, run.prevFrom, run.prevKind, run.prevPattern, run.prevI, run.prevJ,
                run.prevRoutes, run.prevVal, run.prevVal2, wards, run.nearest, run.nearBus, ms);
    }

    /** State of one search. Not shared between threads. */
    private static final class Run {
        final TransitGraph g;
        final double oLat, oLon, lim;
        final int limit, when;
        final boolean bus;
        final Access access;
        final boolean rideFix;
        /** Nodes per layer: bus stops then metro states. With the ride fix there are two layers. */
        final int nb, base, n;
        final double[] dist;
        final int[] prevFrom, prevPattern, prevI, prevJ, prevRoutes;
        final Kind[] prevKind;
        final double[] prevVal, prevVal2;
        final boolean[] done, rode;
        final MinHeap heap = new MinHeap();
        final double traffic;
        NearestStation nearest;
        NearestBusStop nearBus;

        // scratch space for counting bus routes together, reset per stop with a stamp
        final int[] accStamp, accP, accI, accJ, accN, accOrder;
        final double[] accF, accR, accBest, accTf;
        int stamp;

        Run(TransitGraph g, SearchQuery q, boolean rideFix) {
            this.g = g;
            this.rideFix = rideFix;
            oLat = q.lat();
            oLon = q.lon();
            lim = q.leg();
            limit = q.limit();
            when = q.when().ordinal();
            bus = q.network() == SearchQuery.Network.ALL;
            access = q.access();
            traffic = Geo.TRAFFIC[when];
            nb = bus ? g.busStopCount : 0;
            base = nb + g.metroStates;
            n = rideFix ? 2 * base : base;
            dist = new double[n];
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            prevFrom = new int[n];
            prevPattern = new int[n];
            prevI = new int[n];
            prevJ = new int[n];
            prevRoutes = new int[n];
            prevKind = new Kind[n];
            prevVal = new double[n];
            prevVal2 = new double[n];
            done = new boolean[n];
            rode = new boolean[n];
            accStamp = new int[nb];
            accP = new int[nb];
            accI = new int[nb];
            accJ = new int[nb];
            accN = new int[nb];
            accOrder = new int[nb];
            accF = new double[nb];
            accR = new double[nb];
            accBest = new double[nb];
            accTf = new double[nb];
        }

        /**
         * Accept a faster way to reach node {@code local} (an index within one layer), if it fits the time limit.
         * {@code from} is the full node number it was reached from (-1 for the office).
         */
        void relax(int local, double c, Kind kind, int from, int pattern, int i, int j, int routes, double val, double val2) {
            boolean ridden = kind == Kind.BUS || kind == Kind.RIDE || (from >= 0 && rode[from]);
            int v = rideFix && ridden ? local + base : local;
            if (c < dist[v] - 1e-9 && c <= limit) {
                dist[v] = c;
                prevFrom[v] = from;
                prevKind[v] = kind;
                prevPattern[v] = pattern;
                prevI[v] = i;
                prevJ[v] = j;
                prevRoutes[v] = routes;
                prevVal[v] = val;
                prevVal2[v] = val2;
                rode[v] = ridden;
                heap.push(c, v);
            }
        }

        void start() {
            List<Station> stations = g.data.stations();
            for (int si = 0; si < stations.size(); si++) {
                Station s = stations.get(si);
                double d = Geo.metres(oLat, oLon, s.lat(), s.lon());
                double m = access.minutes(d);
                if (nearest == null || m < nearest.minutes()) nearest = new NearestStation(si, s.name(), m, d);
                if (m <= lim) {
                    for (int k : g.stationStates[si]) {
                        double w = Geo.ENTRY + g.waitMin[k][when];
                        relax(nb + k, m + w, Kind.START_METRO, -1, -1, -1, -1, 0, m, w);
                    }
                }
            }
            if (bus) {
                double r = access == Access.WALK ? lim * 80 / 1.3 : Math.max(0, (lim - 5) * 300 / 1.4);
                StopGrid.Near c = g.grid.near(oLat, oLon, Math.max(r, 1500));
                for (int k = 0; k < c.size(); k++) {
                    int i = c.stop(k);
                    double d = c.metres(k), m = access.minutes(d);
                    if (nearBus == null || d < nearBus.metres()) {
                        nearBus = new NearestBusStop(i, g.stopName[i], d, m, g.stopPatterns[i].length / 2);
                    }
                    if (m <= lim) relax(i, m, Kind.START_BUS, -1, -1, -1, -1, 0, m, 0);
                }
            }
        }

        void loop() {
            while (heap.size() > 0) {
                double d = heap.topCost();
                int u = heap.topNode();
                heap.pop();
                if (done[u] || d > dist[u]) continue;
                done[u] = true;
                int local = u % base;
                if (local < nb) expandBusStop(u, local, d);
                else expandMetroState(u, local, d);
            }
        }

        void expandBusStop(int u, int lu, double d) {
            int[] sp = g.stopPatterns[lu];
            stamp++;
            int orderSize = 0;
            // pass 1: fastest ride to each later stop
            for (int q = 0; q < sp.length; q += 2) {
                int pi = sp[q], i = sp[q + 1];
                if (g.patFreq[pi][when] == 0) continue;
                int[] stops = g.patStops[pi];
                float[] cum = g.patCum[pi];
                for (int j = i + 1; j < stops.length; j++) {
                    double ride = ((double) cum[j] - (double) cum[i]) * traffic;
                    if (d + ride > limit) break;
                    int t = stops[j];
                    if (accStamp[t] != stamp) {
                        accStamp[t] = stamp;
                        accOrder[orderSize++] = t;
                        accF[t] = 0;
                        accR[t] = 0;
                        accBest[t] = ride;
                        accP[t] = pi;
                        accI[t] = i;
                        accJ[t] = j;
                        accN[t] = 0;
                        accTf[t] = 0;
                    } else if (ride < accBest[t]) {
                        accBest[t] = ride;
                        accP[t] = pi;
                        accI[t] = i;
                        accJ[t] = j;
                    }
                }
            }
            // pass 2: count together only routes nearly as fast as the fastest; show the most frequent one
            for (int q = 0; q < sp.length; q += 2) {
                int pi = sp[q], i = sp[q + 1];
                double f = g.patFreq[pi][when];
                if (f == 0) continue;
                int[] stops = g.patStops[pi];
                float[] cum = g.patCum[pi];
                for (int j = i + 1; j < stops.length; j++) {
                    double ride = ((double) cum[j] - (double) cum[i]) * traffic;
                    if (d + ride > limit) break;
                    int t = stops[j];
                    if (ride <= accBest[t] * 1.25 + 2) {
                        accF[t] += f;
                        accR[t] += f * ride;
                        accN[t]++;
                        if (f > accTf[t]) {
                            accTf[t] = f;
                            accP[t] = pi;
                            accI[t] = i;
                            accJ[t] = j;
                        }
                    }
                }
            }
            for (int k = 0; k < orderSize; k++) {
                int t = accOrder[k];
                double wait = Math.min(Geo.WAIT_CAP, Math.max(Geo.WAIT_MIN, 1 / (2 * accF[t])));
                double ride = accR[t] / accF[t];
                relax(t, d + wait + ride, Kind.BUS, u, accP[t], accI[t], accJ[t], accN[t], wait, ride);
            }
            int[] ws = g.walkStop[lu];
            double[] wm = g.walkMetres[lu];
            for (int k = 0; k < ws.length; k++) {
                double m = Geo.walkMin(wm[k]);
                relax(ws[k], d + m, Kind.WALK, u, -1, -1, -1, 0, m, 0);
            }
            int[] sm = g.metroOfStop[lu];
            double[] smm = g.metroOfStopMetres[lu];
            for (int k = 0; k < sm.length; k++) {
                double m = Geo.walkMin(smm[k]);
                for (int state : g.stationStates[sm[k]]) {
                    double w = Geo.ENTRY + g.waitMin[state][when];
                    relax(nb + state, d + m + w, Kind.ENTER, u, -1, -1, -1, 0, m, w);
                }
            }
        }

        void expandMetroState(int u, int lu, double d) {
            int k = lu - nb;
            for (MetroArc e : g.msOut[k]) {
                double w = e.ride() ? e.minutes() : Geo.CHANGE + g.waitMin[e.to()][when];
                relax(nb + e.to(), d + w, e.ride() ? Kind.RIDE : Kind.CHANGE, u, -1, -1, -1, 0, w, 0);
            }
            if (bus) {
                int si = g.msStation[k];
                int[] bs = g.stopsOfStation[si];
                double[] bm = g.stopsOfStationMetres[si];
                for (int q = 0; q < bs.length; q++) {
                    double m = Geo.walkMin(bm[q]);
                    relax(bs[q], d + Geo.EXIT + m, Kind.EXIT, u, -1, -1, -1, 0, m, 0);
                }
            }
        }

        List<WardResult> wards() {
            int ns = g.data.stations().size();
            double[] arrT = new double[ns];
            int[] arrNode = new int[ns];
            Arrays.fill(arrNode, -1);
            for (int layer = 0; layer * base < n; layer++) {
                for (int k = 0; k < g.metroStates; k++) {
                    int v = nb + k + layer * base;
                    double c = dist[v];
                    int si = g.msStation[k];
                    if (c < Double.POSITIVE_INFINITY && rode[v] && (arrNode[si] < 0 || c < arrT[si])) {
                        arrT[si] = c;
                        arrNode[si] = v;
                    }
                }
            }
            List<WardResult> out = new ArrayList<>(g.data.wards().size());
            for (Ward w : g.data.wards()) {
                double bestT = 0, last = 0;
                int node = -1;
                boolean found = false, direct = false, byMetro = false;
                double directMin = access.minutes(Geo.metres(oLat, oLon, w.centre()[0], w.centre()[1]));
                if (directMin <= lim) {
                    found = true;
                    bestT = directMin;
                    direct = true;
                    last = directMin;
                }
                for (int k = 0; k < w.metroStation().length; k++) {
                    int si = w.metroStation()[k];
                    if (arrNode[si] < 0) continue;
                    double m = access.minutes(w.metroMetres()[k] + Geo.INTO);
                    if (m > lim) continue;
                    double t = arrT[si] + Geo.EXIT + m;
                    if (!found || t < bestT) {
                        found = true;
                        bestT = t;
                        node = arrNode[si];
                        last = m;
                        direct = false;
                        byMetro = true;
                    }
                }
                if (bus) {
                    int[] stops = g.wardStops[w.index()];
                    int[] metres = g.wardStopMetres[w.index()];
                    for (int q = 0; q < stops.length; q++) {
                        for (int layer = 0; layer * base < n; layer++) {
                            int v = stops[q] + layer * base;
                            double c = dist[v];
                            if (c == Double.POSITIVE_INFINITY || !rode[v]) continue;
                            double m = access.minutes(metres[q] + Geo.INTO);
                            if (m > lim) continue;
                            if (!found || c + m < bestT) {
                                found = true;
                                bestT = c + m;
                                node = v;
                                last = m;
                                direct = false;
                                byMetro = false;
                            }
                        }
                    }
                }
                out.add(found ? new WardResult(w.index(), bestT, (int) Math.round(bestT), direct, byMetro, node, last) : null);
            }
            return Collections.unmodifiableList(out);
        }
    }
}
