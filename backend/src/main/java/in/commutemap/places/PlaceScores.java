package in.commutemap.places;

import in.commutemap.data.TransitData;

/**
 * Everyday-needs scores per ward, worked out once at startup. Same formula as the prototype's
 * {@code buildPlaces()}:
 * <ul>
 *   <li>for each place type, a ward's density is its count per km² (area at least 0.2 km²);</li>
 *   <li>its score is its rank among all wards, 0-100: (wards with lower density + half of those with the
 *       same density) / number of wards; a ward with none mapped scores 0;</li>
 *   <li>the everyday needs score is the average of the type scores.</li>
 * </ul>
 */
public final class PlaceScores {

    private final int[][] scores;   // [ward][type]
    private final int[] overall;    // [ward]

    public PlaceScores(TransitData d) {
        TransitData.Places p = d.places();
        int n = d.wards().size();
        int types = p == null ? 0 : p.categories().size();
        scores = new int[n][types];
        overall = new int[n];
        if (p == null) return;
        for (int k = 0; k < types; k++) {
            double[] dens = new double[n];
            for (int i = 0; i < n; i++) dens[i] = p.count()[i][k] / Math.max(d.wards().get(i).areaKm2(), 0.2);
            for (int i = 0; i < n; i++) {
                if (p.count()[i][k] == 0) continue;
                int lo = 0, eq = 0;
                for (double x : dens) {
                    if (x < dens[i]) lo++;
                    else if (x == dens[i]) eq++;
                }
                scores[i][k] = (int) Math.round(100 * (lo + eq / 2.0) / n);
            }
        }
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (int k = 0; k < types; k++) sum += scores[i][k];
            overall[i] = types == 0 ? 0 : (int) Math.round((double) sum / types);
        }
    }

    /** Score 0-100 for each place type. */
    public int[] scores(int ward) {
        return scores[ward].clone();
    }

    /** The everyday needs score, 0-100. */
    public int overall(int ward) {
        return overall[ward];
    }
}
