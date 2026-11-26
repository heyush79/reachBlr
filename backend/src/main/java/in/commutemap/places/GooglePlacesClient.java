package in.commutemap.places;

import com.fasterxml.jackson.databind.JsonNode;
import in.commutemap.config.GoogleProperties;
import in.commutemap.routing.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls Google Places API (New) Nearby Search.
 *
 * <p>Google's terms: nothing from the answer is stored (only place IDs may be kept, and we keep nothing),
 * and results are shown as a list with "Google Maps" attribution, never on our non-Google map.
 * The field mask asks only for fields billed as "Nearby Search Pro", the cheapest tier.
 */
@Component
public class GooglePlacesClient {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesClient.class);

    /** Only Nearby Search Pro fields. Adding e.g. places.rating would move every call to a dearer tier. */
    static final String FIELD_MASK = "places.id,places.displayName,places.shortFormattedAddress,places.location,"
            + "places.googleMapsUri,places.attributions";

    /** Our place types -> Google's "Table A" place types. */
    public static final Map<String, List<String>> TYPES = typeMap();

    private final GoogleProperties props;
    private final RestClient http;

    public GooglePlacesClient(GoogleProperties props, RestClient.Builder builder) {
        this.props = props;
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.http = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    public boolean configured() {
        return props.configured();
    }

    /** Places of one of our types near a point, nearest first. */
    public List<NearbyPlace> nearby(double lat, double lon, String type) {
        List<String> googleTypes = TYPES.get(type);
        if (googleTypes == null) throw new IllegalArgumentException("unknown place type " + type);
        Map<String, Object> body = Map.of(
                "includedTypes", googleTypes,
                "maxResultCount", props.maxResults(),
                "rankPreference", "DISTANCE",
                "locationRestriction", Map.of("circle", Map.of(
                        "center", Map.of("latitude", lat, "longitude", lon),
                        "radius", props.radiusMetres())));
        JsonNode answer;
        try {
            answer = http.post()
                    .uri("/v1/places:searchNearby")
                    .header("X-Goog-Api-Key", props.apiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // log the status only: the body may echo request details
            log.warn("Google Places answered {} for type {}", e.getStatusCode().value(), type);
            throw new GoogleUnavailableException("Google Places did not answer. Please try again later.");
        } catch (RestClientException e) {
            log.warn("Google Places could not be reached: {}", e.getClass().getSimpleName());
            throw new GoogleUnavailableException("Google Places could not be reached. Please try again later.");
        }
        List<NearbyPlace> out = new ArrayList<>();
        if (answer == null || !answer.has("places")) return out;   // no results: Google sends {}
        for (JsonNode p : answer.get("places")) {
            double plat = p.path("location").path("latitude").asDouble(), plon = p.path("location").path("longitude").asDouble();
            List<NearbyPlace.Attribution> attributions = new ArrayList<>();
            for (JsonNode a : p.path("attributions")) {
                attributions.add(new NearbyPlace.Attribution(a.path("provider").asText(""), a.path("providerUri").asText("")));
            }
            out.add(new NearbyPlace(p.path("id").asText(), p.path("displayName").path("text").asText(""),
                    p.path("shortFormattedAddress").asText(""), plat, plon,
                    (int) Math.round(Geo.metres(lat, lon, plat, plon)), p.path("googleMapsUri").asText(""), attributions));
        }
        return out;
    }

    private static Map<String, List<String>> typeMap() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("gym", List.of("gym", "fitness_center"));
        m.put("park", List.of("park"));
        m.put("health", List.of("hospital", "doctor", "medical_clinic", "medical_center"));
        m.put("school", List.of("school"));
        m.put("grocery", List.of("grocery_store", "supermarket"));
        m.put("pharmacy", List.of("pharmacy", "drugstore"));
        m.put("food", List.of("restaurant", "cafe", "fast_food_restaurant"));
        return Map.copyOf(m);
    }

    /** One result. {@code distanceMetres} is from the search point, worked out on the fly (not stored). */
    public record NearbyPlace(String id, String name, String address, double lat, double lon, int distanceMetres,
                              String googleMapsUri, List<Attribution> attributions) {

        /** A data provider Google says must be shown with the result. */
        public record Attribution(String provider, String providerUri) {}
    }

    /** Google is not set up, did not answer, or could not be reached. */
    public static final class GoogleUnavailableException extends RuntimeException {
        public GoogleUnavailableException(String message) {
            super(message);
        }
    }
}
