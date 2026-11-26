package in.commutemap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.util.List;

/**
 * Settings under {@code commute.*} in application.yml (each can be overridden by an environment variable,
 * e.g. COMMUTE_DATA_DIR).
 *
 * @param dataDir        folder with metro.json, wards.json, bus.json.gz and places.json. Kept as text: Spring's
 *                       own Path conversion treats "../data" as a web resource and rejects it.
 * @param allowedOrigins browser origins allowed to call the API from another site (CORS)
 * @param offices        the preset offices shown in the app
 * @param searchCacheSize how many recent searches to keep, so a trip request right after a search is instant
 * @param rideFix        keep "no ride yet" and "has ridden" times apart at every stop (see {@link in.commutemap.routing.Router});
 *                       off by default so results match the prototype
 */
@ConfigurationProperties("commute")
public record CommuteProperties(
        @DefaultValue("../data") String dataDir,
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins,
        List<Office> offices,
        @DefaultValue("32") int searchCacheSize,
        @DefaultValue("false") boolean rideFix) {

    public record Office(String name, double lat, double lon) {}

    public CommuteProperties {
        offices = offices == null ? List.of() : List.copyOf(offices);
    }

    public Path dataPath() {
        return Path.of(dataDir);
    }
}
