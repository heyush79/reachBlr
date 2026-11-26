package in.commutemap.routing;

/**
 * Maths that must give exactly the same bits as the browser prototype.
 *
 * <p>{@code Math.hypot} in Java and in V8 (Chrome, Node) can differ in the last bit, which can flip a
 * "within 250 m" test or a rounding to whole minutes. This is V8's algorithm (normalise by the larger
 * value, Kahan-summed squares), checked against Node's {@code Math.hypot} on a million random pairs.
 */
final class JsMath {

    private JsMath() {}

    static double hypot(double x, double y) {
        double ax = Math.abs(x), ay = Math.abs(y);
        if (Double.isInfinite(ax) || Double.isInfinite(ay)) return Double.POSITIVE_INFINITY;
        if (Double.isNaN(ax) || Double.isNaN(ay)) return Double.NaN;
        double max = Math.max(ax, ay);
        if (max == 0) return 0;
        double sum = 0, compensation = 0;
        double n = ax / max;
        double summand = n * n - compensation;
        double preliminary = sum + summand;
        compensation = (preliminary - sum) - summand;
        sum = preliminary;
        n = ay / max;
        summand = n * n - compensation;
        preliminary = sum + summand;
        sum = preliminary;
        return Math.sqrt(sum) * max;
    }
}
