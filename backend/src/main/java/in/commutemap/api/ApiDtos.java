package in.commutemap.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.commutemap.trip.Trip;

import java.util.List;

/** Shapes of the JSON responses. */
public final class ApiDtos {

    private ApiDtos() {}

    /**
     * Answer to /api/reach.
     *
     * @param reachable wards within the limit
     * @param wards     every ward with a trip, quickest first; {@code overLimit} ones are just over the limit
     *                  once the walk into the ward is added (the map tooltip still shows their time)
     */
    public record ReachResponse(Query query, long reachable, List<WardTime> wards,
                                NearestStation nearestStation, NearestBusStop nearestBusStop, double searchMs) {}

    public record Query(double lat, double lon, int limit, String mode, int leg, String when, String net) {}

    public record WardTime(int ward, String name, int minutes, boolean overLimit, boolean direct, List<String> via) {}

    /** {@code minutes} by the chosen way of getting to a stop; {@code withinLeg} if that fits the trip-to-stop limit. */
    public record NearestStation(String name, double minutes, int metres, boolean withinLeg) {}

    /** {@code routes}: route patterns that stop there. */
    public record NearestBusStop(String name, int metres, int walkMinutes, int routes) {}

    /** Answer to /api/trip. {@code trip} is null when no trip fits the limits. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TripResponse(int ward, String wardName, Trip trip) {}
}
