package in.commutemap.routing;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Bus stops bucketed into cells of 0.01 degrees (about 1.1 km), for fast "stops near here" lookups.
 * Cells are visited and stops listed in the same order as the prototype, which keeps tie-breaking identical.
 */
final class StopGrid {

    private static final int[] EMPTY = new int[0];
    private final double[] lat, lon;
    private final Map<Integer, int[]> cells = new HashMap<>();

    StopGrid(double[] lat, double[] lon) {
        this.lat = lat;
        this.lon = lon;
        Map<Integer, TransitGraph.IntList> tmp = new HashMap<>();
        for (int i = 0; i < lat.length; i++) {
            tmp.computeIfAbsent(key(row(lat[i]), col(lon[i])), k -> new TransitGraph.IntList()).add(i);
        }
        tmp.forEach((k, v) -> cells.put(k, v.toArray()));
    }

    static int row(double lat) {
        return (int) Math.floor(lat / 0.01);
    }

    static int col(double lon) {
        return (int) Math.floor(lon / 0.01);
    }

    private static int key(int row, int col) {
        return row * 100000 + col;
    }

    int[] cell(int row, int col) {
        return cells.getOrDefault(key(row, col), EMPTY);
    }

    /** Stops within {@code radius} metres of the point, with their distances. */
    Near near(double la, double lo, double radius) {
        Near out = new Near();
        int cr = (int) Math.ceil(radius / 1100);
        int r0 = row(la), c0 = col(lo);
        for (int dr = -cr; dr <= cr; dr++) {
            for (int dc = -cr; dc <= cr; dc++) {
                for (int i : cell(r0 + dr, c0 + dc)) {
                    double d = Geo.metres(la, lo, lat[i], lon[i]);
                    if (d <= radius) out.add(i, d);
                }
            }
        }
        return out;
    }

    static final class Near {
        private int[] stops = new int[16];
        private double[] metres = new double[16];
        private int n;

        void add(int stop, double m) {
            if (n == stops.length) {
                stops = Arrays.copyOf(stops, n * 2);
                metres = Arrays.copyOf(metres, n * 2);
            }
            stops[n] = stop;
            metres[n++] = m;
        }

        int size() {
            return n;
        }

        int stop(int k) {
            return stops[k];
        }

        double metres(int k) {
            return metres[k];
        }
    }
}
