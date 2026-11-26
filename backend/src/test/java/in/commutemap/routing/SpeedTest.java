package in.commutemap.routing;

import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.SearchQuery.Network;
import in.commutemap.routing.SearchQuery.TimeOfDay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The longest search (90 min, metro and bus) must answer in under 300 ms. */
class SpeedTest {

    private static final double[][] OFFICES = {
            {12.9855, 77.7368}, {13.0472, 77.6205}, {12.9298, 77.6871}, {12.9406, 77.6967},
            {12.9806, 77.6626}, {12.9548, 77.6447}, {12.8452, 77.6602}, {12.9716, 77.5960},
            {12.9352, 77.6245}, {12.9116, 77.6389}, {12.9128, 77.4993}};

    @Test
    void ninetyMinuteSearchIsUnder300Ms() {
        check(new Router(TestGraph.get(), false), "copy mode");
    }

    @Test
    void ninetyMinuteSearchIsUnder300MsWithTheRideFix() {
        check(new Router(TestGraph.get(), true), "ride fix");
    }

    private static void check(Router router, String mode) {
        // warm up, as on a running server where the JIT compiler has optimised the search
        for (int k = 0; k < 20; k++) {
            for (double[] o : OFFICES) {
                router.search(new SearchQuery(o[0], o[1], 90, Access.WALK, 15, TimeOfDay.MORNING, Network.ALL));
            }
        }
        double worst = 0;
        String worstCase = "";
        for (double[] o : OFFICES) {
            for (Access a : Access.values()) {
                for (TimeOfDay when : TimeOfDay.values()) {
                    int leg = a == Access.WALK ? 15 : 30;
                    long t0 = System.nanoTime();
                    router.search(new SearchQuery(o[0], o[1], 90, a, leg, when, Network.ALL));
                    double ms = (System.nanoTime() - t0) / 1e6;
                    if (ms > worst) {
                        worst = ms;
                        worstCase = o[0] + "," + o[1] + " " + a + " " + when;
                    }
                }
            }
        }
        System.out.printf("slowest 90 min search, %s: %.1f ms (%s)%n", mode, worst, worstCase);
        assertTrue(worst < 300, "slowest search took " + worst + " ms");
    }
}
