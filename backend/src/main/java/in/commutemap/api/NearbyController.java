package in.commutemap.api;

import in.commutemap.config.GoogleProperties;
import in.commutemap.places.GooglePlacesClient;
import in.commutemap.places.GooglePlacesClient.NearbyPlace;
import in.commutemap.places.PlacesRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A proxy to Google Places Nearby Search, so the API key stays on the server.
 * Answers are never stored (Cache-Control: no-store) and are rate limited per visitor and per day.
 */
@RestController
@RequestMapping("/api/places")
public class NearbyController {

    private final GooglePlacesClient google;
    private final PlacesRateLimiter limiter;
    private final GoogleProperties props;

    public NearbyController(GooglePlacesClient google, PlacesRateLimiter limiter, GoogleProperties props) {
        this.google = google;
        this.limiter = limiter;
        this.props = props;
    }

    public record NearbyParams(
            @NotNull(message = "is required")
            @DecimalMin(value = "12.6", message = "must be in the Bengaluru area (12.6 to 13.4)")
            @DecimalMax(value = "13.4", message = "must be in the Bengaluru area (12.6 to 13.4)")
            Double lat,
            @NotNull(message = "is required")
            @DecimalMin(value = "77.2", message = "must be in the Bengaluru area (77.2 to 78.0)")
            @DecimalMax(value = "78.0", message = "must be in the Bengaluru area (77.2 to 78.0)")
            Double lon,
            @NotNull(message = "is required")
            @Pattern(regexp = "gym|park|health|school|grocery|pharmacy|food",
                    message = "must be gym, park, health, school, grocery, pharmacy or food")
            String type) {}

    /** {@code attribution} must be shown with the list ("Google Maps"), as must each place's own attributions. */
    public record NearbyResponse(String type, double radiusMetres, List<NearbyPlace> places, String attribution) {}

    @GetMapping("/nearby")
    public ResponseEntity<NearbyResponse> nearby(@Valid @ModelAttribute NearbyParams p, HttpServletRequest request) {
        if (!google.configured()) throw new GoogleNotConfiguredException();
        limiter.take(request.getRemoteAddr());
        List<NearbyPlace> places = google.nearby(p.lat(), p.lon(), p.type());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NearbyResponse(p.type(), props.radiusMetres(), places, "Google Maps"));
    }

    public static final class GoogleNotConfiguredException extends RuntimeException {
        public GoogleNotConfiguredException() {
            super("Nearby places from Google are not set up on this server.");
        }
    }
}
