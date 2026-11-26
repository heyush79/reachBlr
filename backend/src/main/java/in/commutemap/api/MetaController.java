package in.commutemap.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.commutemap.config.CommuteProperties;
import in.commutemap.config.GoogleProperties;
import in.commutemap.data.TransitData;
import in.commutemap.data.TransitData.Ward;
import in.commutemap.places.PlaceScores;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Data that only changes when the app is redeployed: offices, metro lines, ward shapes.
 * Built once at startup. Browsers keep a copy but re-check it on every visit with its ETag, so an
 * unchanged copy costs only a tiny 304 reply, and a redeploy with new data is picked up at once.
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");
    private final Cached meta;
    private final Cached wards;

    public MetaController(TransitData data, CommuteProperties props, PlaceScores scores, GoogleProperties google,
                          ObjectMapper json) throws JsonProcessingException {
        this.meta = new Cached(json.writeValueAsBytes(meta(data, props, google.configured())));
        this.wards = new Cached(json.writeValueAsBytes(wardsGeoJson(data, scores)));
    }

    /** Preset offices, metro lines and stations, place types and data versions. */
    @GetMapping("/meta")
    public ResponseEntity<byte[]> meta(@RequestHeader(value = "If-None-Match", required = false) String etag) {
        return meta.respond(etag, MediaType.APPLICATION_JSON);
    }

    /**
     * All 243 wards as GeoJSON (the standard JSON format for map shapes): name, number, area, and per place type
     * (in the order of {@code placeTypes} in /api/meta) the count inside, the nearest one from the centre in
     * units of 10 m (-1 = none), and the score; plus the everyday needs score.
     */
    @GetMapping("/wards")
    public ResponseEntity<byte[]> wards(@RequestHeader(value = "If-None-Match", required = false) String etag) {
        return wards.respond(etag, GEO_JSON);
    }

    private static Map<String, Object> meta(TransitData d, CommuteProperties props, boolean googlePlaces) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offices", props.offices());
        List<Map<String, Object>> lines = new ArrayList<>();
        d.lines().values().forEach(l -> lines.add(Map.of("id", l.id(), "name", l.name(), "color", l.color(),
                "path", l.path())));
        m.put("lines", lines);
        List<Map<String, Object>> stations = new ArrayList<>();
        d.stations().forEach(s -> stations.add(Map.of("id", s.id(), "name", s.name(), "lat", s.lat(), "lon", s.lon(),
                "lines", s.lines())));
        m.put("stations", stations);
        m.put("placeTypes", d.places() == null ? List.of() : d.places().categories());
        m.put("placesAsOf", d.places() == null ? null : d.places().base());
        Map<String, Object> feeds = new LinkedHashMap<>();
        feeds.put("metro", d.feeds().metro());
        feeds.put("bus", d.feeds().bus());
        feeds.put("wards", d.feeds().wards());
        feeds.put("places", d.feeds().places());
        m.put("feeds", feeds);
        m.put("limits", Map.of("limit", List.of(15, 90), "leg", List.of(5, 30)));
        m.put("rideFix", props.rideFix());
        m.put("googlePlaces", googlePlaces);
        return m;
    }

    private static Map<String, Object> wardsGeoJson(TransitData d, PlaceScores scores) {
        List<Map<String, Object>> features = new ArrayList<>();
        for (Ward w : d.wards()) {
            List<List<List<double[]>>> polygons = new ArrayList<>();
            for (double[][] ring : w.rings()) {
                List<double[]> lonLat = new ArrayList<>(ring.length);
                for (double[] p : ring) lonLat.add(new double[]{p[1], p[0]});   // GeoJSON is [lon, lat]
                polygons.add(List.of(lonLat));
            }
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("index", w.index());
            props.put("no", w.no());
            props.put("name", w.name());
            props.put("areaKm2", w.areaKm2());
            props.put("centre", w.centre());
            if (d.places() != null) {
                props.put("count", d.places().count()[w.index()]);
                props.put("nearest10m", d.places().nearest10m()[w.index()]);
                props.put("scores", scores.scores(w.index()));
                props.put("score", scores.overall(w.index()));
            }
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("type", "Feature");
            f.put("id", w.index());
            f.put("properties", props);
            f.put("geometry", Map.of("type", "MultiPolygon", "coordinates", polygons));
            features.add(f);
        }
        return Map.of("type", "FeatureCollection", "features", features);
    }

    /** A pre-serialised response with an ETag, so unchanged data is answered with 304 Not Modified. */
    private static final class Cached {
        private final byte[] body;
        private final String etag;

        Cached(byte[] body) {
            this.body = body;
            // weak ETag: Tomcat does not gzip responses that carry a strong one
            this.etag = "W/\"" + DigestUtils.md5DigestAsHex(body) + "\"";
        }

        ResponseEntity<byte[]> respond(String ifNoneMatch, MediaType type) {
            CacheControl cache = CacheControl.noCache().cachePublic();
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build();
            }
            return ResponseEntity.ok().contentType(type).eTag(etag).cacheControl(cache).body(body);
        }
    }
}
