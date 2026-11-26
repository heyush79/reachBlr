package in.commutemap.routing;

import in.commutemap.data.TransitData;
import in.commutemap.data.TransitData.Pattern;
import in.commutemap.data.TransitData.Station;
import in.commutemap.data.TransitData.Ward;

import java.util.*;

/**
 * The search structures, built once at startup from {@link TransitData} and never changed afterwards,
 * so any number of searches can read them at the same time.
 *
 * <p>Graph nodes are numbered: bus stops first (0..busStopCount-1), then metro "states", one per
 * (station, line) pair. A station on two lines has two states, joined by "change" links.
 */
public final class TransitGraph {

    final TransitData data;

    // ---- metro ----
    final int metroStates;
    final int[] msStation;          // state -> station index
    final String[] msLine;          // state -> line id
    final MetroArc[][] msOut;       // state -> outgoing links (rides first, then changes)
    final Map<String, Integer> msKey = new HashMap<>();   // "STATIONID|LINE" -> state
    final int[][] stationStates;    // station -> its states, in line order
    final double[][] waitMin;       // state -> [morning, evening] wait in minutes (half the train gap)

    // ---- bus ----
    final int busStopCount;
    final double[] lat, lon;
    final String[] stopName;
    final String[] patLabel;
    final int[][] patStops;
    final float[][] patCum;         // minutes since the first stop (stored as float, like the prototype's Float32Array)
    final double[][] patFreq;       // pattern -> [buses per minute morning, evening]
    final int[][] stopPatterns;     // stop -> pairs (pattern, position), for every position except the last
    final int[][] walkStop;         // stop -> nearby stops within 250 m
    final double[][] walkMetres;
    final int[][] metroOfStop;      // stop -> metro stations within 400 m
    final double[][] metroOfStopMetres;
    final int[][] stopsOfStation;   // station -> bus stops within 400 m
    final double[][] stopsOfStationMetres;
    final StopGrid grid;

    // ---- wards ----
    final int[][] wardStops;        // ward -> bus stops within 1.6 km of its edge
    final int[][] wardStopMetres;   // rounded metres

    record MetroArc(int to, boolean ride, double minutes, String line) {}

    public TransitGraph(TransitData d) {
        this.data = d;
        List<Station> stations = d.stations();

        // metro states, in station order then line order
        List<Integer> stStation = new ArrayList<>();
        List<String> stLine = new ArrayList<>();
        stationStates = new int[stations.size()][];
        for (int si = 0; si < stations.size(); si++) {
            Station s = stations.get(si);
            stationStates[si] = new int[s.lines().size()];
            for (int k = 0; k < s.lines().size(); k++) {
                String line = s.lines().get(k);
                msKey.put(s.id() + "|" + line, stStation.size());
                stationStates[si][k] = stStation.size();
                stStation.add(si);
                stLine.add(line);
            }
        }
        metroStates = stStation.size();
        msStation = stStation.stream().mapToInt(Integer::intValue).toArray();
        msLine = stLine.toArray(String[]::new);
        List<List<MetroArc>> out = new ArrayList<>();
        for (int i = 0; i < metroStates; i++) out.add(new ArrayList<>());
        for (TransitData.Edge e : d.edges()) {
            out.get(msKey.get(e.from() + "|" + e.line()))
                    .add(new MetroArc(msKey.get(e.to() + "|" + e.line()), true, e.seconds() / 60.0, null));
        }
        for (Station s : stations) {
            for (String l1 : s.lines()) {
                for (String l2 : s.lines()) {
                    if (!l1.equals(l2)) {
                        out.get(msKey.get(s.id() + "|" + l1)).add(new MetroArc(msKey.get(s.id() + "|" + l2), false, 0, l2));
                    }
                }
            }
        }
        msOut = out.stream().map(l -> l.toArray(MetroArc[]::new)).toArray(MetroArc[][]::new);
        waitMin = new double[metroStates][2];
        for (int k = 0; k < metroStates; k++) {
            String sid = stations.get(msStation[k]).id();
            for (int w = 0; w < 2; w++) waitMin[k][w] = waitMinutes(d, sid, msLine[k], w);
        }

        // bus stops and patterns
        TransitData.BusStops bs = d.busStops();
        busStopCount = bs.size();
        lat = bs.lat();
        lon = bs.lon();
        stopName = bs.name();
        int np = d.patterns().size();
        patLabel = new String[np];
        patStops = new int[np][];
        patCum = new float[np][];
        patFreq = new double[np][];
        for (int pi = 0; pi < np; pi++) {
            Pattern p = d.patterns().get(pi);
            patLabel[pi] = p.label();
            patStops[pi] = p.stops();
            float[] cum = new float[p.cumTenths().length];
            for (int i = 0; i < cum.length; i++) cum[i] = (float) (p.cumTenths()[i] / 10.0);
            patCum[pi] = cum;
            patFreq[pi] = new double[]{p.gapMorning() != 0 ? 1 / p.gapMorning() : 0, p.gapEvening() != 0 ? 1 / p.gapEvening() : 0};
        }
        IntList[] sp = IntList.array(busStopCount);
        for (int pi = 0; pi < np; pi++) {
            int[] s = patStops[pi];
            for (int i = 0; i < s.length - 1; i++) sp[s[i]].add(pi, i);
        }
        stopPatterns = IntList.toArrays(sp);

        grid = new StopGrid(lat, lon);
        walkStop = new int[busStopCount][];
        walkMetres = new double[busStopCount][];
        for (int i = 0; i < busStopCount; i++) {
            StopGrid.Near nb = grid.near(lat[i], lon[i], Geo.BUS_WALK);
            IntList ids = new IntList();
            DoubleList ms = new DoubleList();
            for (int k = 0; k < nb.size(); k++) {
                if (nb.stop(k) != i) {
                    ids.add(nb.stop(k));
                    ms.add(nb.metres(k));
                }
            }
            walkStop[i] = ids.toArray();
            walkMetres[i] = ms.toArray();
        }

        IntList[] smIds = IntList.array(busStopCount);
        DoubleList[] smMetres = DoubleList.array(busStopCount);
        stopsOfStation = new int[stations.size()][];
        stopsOfStationMetres = new double[stations.size()][];
        for (int si = 0; si < stations.size(); si++) {
            Station s = stations.get(si);
            StopGrid.Near nb = grid.near(s.lat(), s.lon(), Geo.METRO_WALK);
            stopsOfStation[si] = new int[nb.size()];
            stopsOfStationMetres[si] = new double[nb.size()];
            for (int k = 0; k < nb.size(); k++) {
                stopsOfStation[si][k] = nb.stop(k);
                stopsOfStationMetres[si][k] = nb.metres(k);
                smIds[nb.stop(k)].add(si);
                smMetres[nb.stop(k)].add(nb.metres(k));
            }
        }
        metroOfStop = IntList.toArrays(smIds);
        metroOfStopMetres = DoubleList.toArrays(smMetres);

        // wards: bus stops within 1.6 km of the (simplified) outline
        int nw = d.wards().size();
        wardStops = new int[nw][];
        wardStopMetres = new int[nw][];
        for (Ward w : d.wards()) {
            double[][][] planar = planarRings(w);
            double la0 = Double.POSITIVE_INFINITY, la1 = Double.NEGATIVE_INFINITY;
            double lo0 = Double.POSITIVE_INFINITY, lo1 = Double.NEGATIVE_INFINITY;
            for (double[][] ring : w.rings()) {
                for (double[] p : ring) {
                    la0 = Math.min(la0, p[0]);
                    la1 = Math.max(la1, p[0]);
                    lo0 = Math.min(lo0, p[1]);
                    lo1 = Math.max(lo1, p[1]);
                }
            }
            lo0 -= Geo.WARD_RADIUS / Geo.MX;
            lo1 += Geo.WARD_RADIUS / Geo.MX;
            la0 -= Geo.WARD_RADIUS / Geo.MY;
            la1 += Geo.WARD_RADIUS / Geo.MY;
            IntList ids = new IntList();
            IntList ms = new IntList();
            for (int rr = (int) Math.floor(la0 / 0.01); rr <= (int) Math.floor(la1 / 0.01); rr++) {
                for (int cc = (int) Math.floor(lo0 / 0.01); cc <= (int) Math.floor(lo1 / 0.01); cc++) {
                    for (int i : grid.cell(rr, cc)) {
                        double dist = Geo.polyDist(lon[i] * Geo.MX, lat[i] * Geo.MY, planar);
                        if (dist <= Geo.WARD_RADIUS) {
                            ids.add(i);
                            ms.add((int) Math.round(dist));
                        }
                    }
                }
            }
            wardStops[w.index()] = ids.toArray();
            wardStopMetres[w.index()] = ms.toArray();
        }
    }

    /** Half the train gap, in minutes. A missing gap counts as 10 minutes. */
    private static double waitMinutes(TransitData d, String station, String line, int when) {
        Map<String, int[]> byLine = d.headways().get(station);
        int[] h = byLine == null ? null : byLine.get(line);
        int seconds = h == null || h[when] == 0 ? Geo.DEFAULT_HEADWAY_S : h[when];
        return seconds / 120.0;
    }

    private static double[][][] planarRings(Ward w) {
        double[][][] out = new double[w.rings().size()][][];
        for (int r = 0; r < out.length; r++) {
            double[][] ring = w.rings().get(r);
            out[r] = new double[ring.length][];
            for (int i = 0; i < ring.length; i++) out[r][i] = new double[]{ring[i][1] * Geo.MX, ring[i][0] * Geo.MY};
        }
        return out;
    }

    public TransitData data() {
        return data;
    }

    public int busStopCount() {
        return busStopCount;
    }

    public int metroStateCount() {
        return metroStates;
    }

    /** Small growable int list, to avoid boxing while building. */
    static final class IntList {
        private int[] a = new int[4];
        private int n;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        void add(int v1, int v2) {
            add(v1);
            add(v2);
        }

        int[] toArray() {
            return Arrays.copyOf(a, n);
        }

        static IntList[] array(int size) {
            IntList[] out = new IntList[size];
            for (int i = 0; i < size; i++) out[i] = new IntList();
            return out;
        }

        static int[][] toArrays(IntList[] lists) {
            int[][] out = new int[lists.length][];
            for (int i = 0; i < lists.length; i++) out[i] = lists[i].toArray();
            return out;
        }
    }

    static final class DoubleList {
        private double[] a = new double[4];
        private int n;

        void add(double v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        double[] toArray() {
            return Arrays.copyOf(a, n);
        }

        static DoubleList[] array(int size) {
            DoubleList[] out = new DoubleList[size];
            for (int i = 0; i < size; i++) out[i] = new DoubleList();
            return out;
        }

        static double[][] toArrays(DoubleList[] lists) {
            double[][] out = new double[lists.length][];
            for (int i = 0; i < lists.length; i++) out[i] = lists[i].toArray();
            return out;
        }
    }
}
