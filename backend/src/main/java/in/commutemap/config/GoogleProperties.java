package in.commutemap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the Google Places proxy, under {@code commute.google.*}.
 *
 * @param apiKey          Places API key, from the GOOGLE_PLACES_API_KEY environment variable only. Empty = feature off.
 * @param baseUrl         Google's API address (tests point it at a local fake)
 * @param perIpPerMinute  lookups allowed per visitor (IP address) per minute
 * @param globalPerDay    lookups allowed for the whole site per day, a hard cap on the bill
 * @param maxResults      places per lookup (Google allows 1-20)
 * @param radiusMetres    search circle around the ward centre
 */
@ConfigurationProperties("commute.google")
public record GoogleProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://places.googleapis.com") String baseUrl,
        @DefaultValue("10") int perIpPerMinute,
        @DefaultValue("500") int globalPerDay,
        @DefaultValue("10") int maxResults,
        @DefaultValue("1000") double radiusMetres) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never print the key. */
    @Override
    public String toString() {
        return "GoogleProperties[configured=" + configured() + ", baseUrl=" + baseUrl + ", perIpPerMinute=" + perIpPerMinute
                + ", globalPerDay=" + globalPerDay + "]";
    }
}
