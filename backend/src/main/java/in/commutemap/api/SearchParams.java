package in.commutemap.api;

import in.commutemap.routing.SearchQuery;
import in.commutemap.routing.SearchQuery.Access;
import in.commutemap.routing.SearchQuery.Network;
import in.commutemap.routing.SearchQuery.TimeOfDay;
import jakarta.validation.constraints.*;

import java.util.Locale;

/**
 * Query-string parameters shared by /api/reach and /api/trip. Only {@code lat} and {@code lon} are
 * required; the rest default to the app's starting values (45 min, walk, 15 min, morning, metro and bus).
 */
public record SearchParams(
        @NotNull(message = "is required")
        @DecimalMin(value = "12.6", message = "must be in the Bengaluru area (12.6 to 13.4)")
        @DecimalMax(value = "13.4", message = "must be in the Bengaluru area (12.6 to 13.4)")
        Double lat,

        @NotNull(message = "is required")
        @DecimalMin(value = "77.2", message = "must be in the Bengaluru area (77.2 to 78.0)")
        @DecimalMax(value = "78.0", message = "must be in the Bengaluru area (77.2 to 78.0)")
        Double lon,

        @Min(value = 15, message = "must be 15 to 90 minutes")
        @Max(value = 90, message = "must be 15 to 90 minutes")
        Integer limit,

        @Pattern(regexp = "(?i)walk|auto", message = "must be walk or auto")
        String mode,

        @Min(value = 5, message = "must be 5 to 30 minutes")
        @Max(value = 30, message = "must be 5 to 30 minutes")
        Integer leg,

        @Pattern(regexp = "(?i)morning|evening", message = "must be morning or evening")
        String when,

        @Pattern(regexp = "(?i)metro|all", message = "must be metro or all")
        String net) {

    public SearchQuery toQuery() {
        return new SearchQuery(lat, lon,
                limit == null ? 45 : limit,
                mode == null ? Access.WALK : Access.valueOf(mode.toUpperCase(Locale.ROOT)),
                leg == null ? 15 : leg,
                when == null ? TimeOfDay.MORNING : TimeOfDay.valueOf(when.toUpperCase(Locale.ROOT)),
                net == null ? Network.ALL : Network.valueOf(net.toUpperCase(Locale.ROOT)));
    }
}
