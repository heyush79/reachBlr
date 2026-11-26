package in.commutemap.routing;

/**
 * Distances and the travel-time rules. All numbers come from the prototype (reference/.../NOTES.md),
 * and the arithmetic is written in the same order so the results match it to the last bit.
 */
public final class Geo {

    private Geo() {}

    // Flat-earth projection around Bengaluru: fine for distances of a few km.
    public static final double LAT0 = 12.97;
    public static final double COS = StrictMath.cos(LAT0 * Math.PI / 180);   // StrictMath = same cos as JavaScript
    /** Metres per degree of longitude and of latitude at Bengaluru. */
    public static final double MX = COS * 111320, MY = 110574;

    /** Minutes added when entering a metro station, leaving one, and changing lines (plus the wait). */
    public static final double ENTRY = 2, EXIT = 1, CHANGE = 3;
    /** Every ward is reached at its nearest edge plus this walk into it, in metres. */
    public static final double INTO = 300;
    /** Bus waits are clamped to 1..15 min (riders can check live bus times). */
    public static final double WAIT_MIN = 1, WAIT_CAP = 15;
    /** Bus timetable ride times are multiplied by these for traffic: morning, evening. */
    public static final double[] TRAFFIC = {1.8, 1.4};
    /** Longest walk between two bus stops, and between a bus stop and a metro station, in metres. */
    public static final double BUS_WALK = 250, METRO_WALK = 400;
    /** Bus stops within this distance of a ward's edge are linked to the ward, in metres. */
    public static final double WARD_RADIUS = 1600;
    /** Gap assumed when a station has no train-gap data: 600 s, so a 5 min wait. */
    public static final int DEFAULT_HEADWAY_S = 600;

    /** Straight-line metres between [lat, lon] points. */
    public static double metres(double lat1, double lon1, double lat2, double lon2) {
        return JsMath.hypot((lon1 - lon2) * MX, (lat1 - lat2) * MY);
    }

    /** Walking: 4.8 km/h (80 m/min) along a path 30% longer than a straight line. */
    public static double walkMin(double metres) {
        return metres * 1.3 / 80;
    }

    /** Auto or cab: 5 min to get one, then 18 km/h (300 m/min) along a road 40% longer than a straight line. */
    public static double autoMin(double metres) {
        return 5 + metres * 1.4 / 300;
    }

    /** Distance from (x, y) to the nearest edge of the rings, 0 if inside. Planar coordinates in metres. */
    public static double polyDist(double x, double y, double[][][] rings) {
        boolean inside = false;
        double best = Double.POSITIVE_INFINITY;
        for (double[][] r : rings) {
            for (int i = 0, j = r.length - 1; i < r.length; j = i++) {
                double xi = r[i][0], yi = r[i][1], xj = r[j][0], yj = r[j][1];
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
                double dx = xj - xi, dy = yj - yi, len = dx * dx + dy * dy;
                double t = len != 0 ? ((x - xi) * dx + (y - yi) * dy) / len : 0;
                t = Math.max(0, Math.min(1, t));
                double ex = xi + t * dx - x, ey = yi + t * dy - y, d = ex * ex + ey * ey;
                if (d < best) best = d;
            }
        }
        return inside ? 0 : Math.sqrt(best);
    }
}
