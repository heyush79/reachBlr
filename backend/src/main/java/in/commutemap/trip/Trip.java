package in.commutemap.trip;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A trip from the office into one ward, step by step.
 *
 * @param via the lines used, e.g. ["Purple Line", "Bus 500-D"]; empty for a direct walk or ride
 */
public record Trip(int ward, String wardName, int minutes, boolean overLimit, boolean direct,
                   List<String> via, List<Step> steps) {

    public enum StepType {
        /** From the office to the first stop (walk, or auto/cab). */
        ACCESS,
        /** A walk between stops, or to a metro station. */
        WALK,
        /** Leave a metro station and walk to a bus stop. */
        EXIT_WALK,
        BUS,
        METRO,
        /** From the last stop into the ward. */
        FINAL,
        /** Straight from the office into the ward, no bus or metro. */
        DIRECT
    }

    /**
     * One step. {@code text} is the plain-English sentence (same wording as the prototype).
     * {@code path} is what to draw, as [lat, lon] points (empty for the last step into the ward).
     * {@code line} is set for metro steps, {@code bus} for bus steps.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(StepType type, String text, int minutes, Integer waitMinutes, String to, Integer stops,
                       MetroLine line, BusRide bus, List<double[]> path) {}

    public record MetroLine(String id, String name, String color) {}

    /** {@code otherRoutes}: how many more route patterns were counted together for this ride. */
    public record BusRide(String label, int otherRoutes) {}
}
