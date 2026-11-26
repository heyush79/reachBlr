package in.commutemap.routing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Expected values were printed by Node's Math.hypot (V8); they must match to the last bit. */
class JsMathTest {

    @ParameterizedTest
    @CsvSource({
            "3, 4, 5",
            "1234.5678, -9876.54321, 9953.404615094974",
            "0.1, 0.2, 0.223606797749979",
            "-2169.799449370498, 0, 2169.799449370498",
            "0.001, 123456.789, 123456.789",
            "1334.3036613628562, -5042.1744, 5215.734746010545",
    })
    void hypotMatchesV8(double x, double y, double expected) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(JsMath.hypot(x, y)));
    }
}
