package in.commutemap.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.commutemap.data.TransitData.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Reads the files written by the Python pipeline (see pipeline/README.md for the formats). */
public final class DataLoader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DataLoader() {}

    public static TransitData load(Path dir) {
        try {
            JsonNode metro = read(dir.resolve("metro.json"));
            JsonNode wardsDoc = read(dir.resolve("wards.json"));
            JsonNode bus;
            try (InputStream in = new GZIPInputStream(Files.newInputStream(dir.resolve("bus.json.gz")))) {
                bus = JSON.readTree(in);
            }
            Path placesFile = dir.resolve("places.json");
            JsonNode places = Files.exists(placesFile) ? read(placesFile) : null;

            List<Station> stations = new ArrayList<>();
            for (JsonNode s : metro.get("stations")) {
                stations.add(new Station(s.get("id").asText(), s.get("name").asText(),
                        s.get("lat").asDouble(), s.get("lon").asDouble(), strings(s.get("lines"))));
            }
            Map<String, Line> lines = new LinkedHashMap<>();
            metro.get("lines").fields().forEachRemaining(e -> {
                JsonNode l = e.getValue();
                lines.put(e.getKey(), new Line(e.getKey(), l.get("name").asText(), l.get("color").asText(),
                        l.get("longName").asText(), points(l.get("path"))));
            });
            List<Edge> edges = new ArrayList<>();
            for (JsonNode e : metro.get("edges")) {
                edges.add(new Edge(e.get(0).asText(), e.get(1).asText(), e.get(2).asText(), e.get(3).asInt()));
            }
            Map<String, Map<String, int[]>> headways = new HashMap<>();
            metro.get("headways").fields().forEachRemaining(st -> {
                Map<String, int[]> byLine = new HashMap<>();
                st.getValue().fields().forEachRemaining(ln -> byLine.put(ln.getKey(),
                        new int[]{ln.getValue().get(0).asInt(0), ln.getValue().get(1).asInt(0)}));   // null -> 0
                headways.put(st.getKey(), byLine);
            });

            List<Ward> wards = new ArrayList<>();
            int wi = 0;
            for (JsonNode w : wardsDoc.get("wards")) {
                List<double[][]> rings = new ArrayList<>();
                for (JsonNode r : w.get("rings")) {
                    rings.add(points(r).toArray(double[][]::new));
                }
                JsonNode near = w.get("metro");
                int[] ms = new int[near.size()];
                int[] mm = new int[near.size()];
                for (int k = 0; k < near.size(); k++) {
                    ms[k] = near.get(k).get(0).asInt();
                    mm[k] = near.get(k).get(1).asInt();
                }
                wards.add(new Ward(wi++, w.get("no").asText(), w.get("name").asText(),
                        new double[]{w.get("centre").get(0).asDouble(), w.get("centre").get(1).asDouble()},
                        w.get("areaKm2").asDouble(), rings, ms, mm));
            }
            List<double[][]> outline = new ArrayList<>();
            for (JsonNode r : wardsDoc.get("outline")) {
                outline.add(points(r).toArray(double[][]::new));
            }

            JsonNode bs = bus.get("stops");
            BusStops busStops = new BusStops(doubles(bs.get("lat")), doubles(bs.get("lon")),
                    strings(bs.get("name")).toArray(String[]::new));
            List<Pattern> patterns = new ArrayList<>();
            for (JsonNode p : bus.get("patterns")) {
                patterns.add(new Pattern(p.get("label").asText(), strings(p.get("routes")),
                        p.get("gapMorning").asDouble(), p.get("gapEvening").asDouble(),
                        ints(p.get("stops")), ints(p.get("cum"))));
            }

            Places pl = places == null ? null : places(places, wards.size());
            Feeds feeds = new Feeds(metro.get("feed").asText(), bus.get("feed").asText(),
                    wardsDoc.get("source").asText(), pl == null ? null : pl.source() + " Data as of " + pl.base() + ".");
            return new TransitData(feeds, List.copyOf(stations), Collections.unmodifiableMap(lines), List.copyOf(edges),
                    headways, List.copyOf(wards), outline, busStops, List.copyOf(patterns), pl);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read transit data from " + dir.toAbsolutePath(), e);
        }
    }

    private static Places places(JsonNode p, int wardCount) {
        List<Category> cats = new ArrayList<>();
        for (JsonNode c : p.get("categories")) {
            cats.add(new Category(c.get("key").asText(), c.get("label").asText(), c.get("short").asText(),
                    c.get("one").asText()));
        }
        JsonNode ws = p.get("wards");
        if (ws.size() != wardCount) {
            throw new IllegalStateException("places.json has " + ws.size() + " wards, wards.json has " + wardCount);
        }
        int[][] count = new int[wardCount][];
        int[][] nearest = new int[wardCount][];
        for (int i = 0; i < wardCount; i++) {
            count[i] = ints(ws.get(i).get("count"));
            nearest[i] = ints(ws.get(i).get("nearest10m"));
        }
        return new Places(p.get("source").asText(), p.get("base").asText(), List.copyOf(cats), count, nearest);
    }

    private static JsonNode read(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            return JSON.readTree(in);
        }
    }

    private static List<String> strings(JsonNode a) {
        List<String> out = new ArrayList<>(a.size());
        a.forEach(n -> out.add(n.asText()));
        return List.copyOf(out);
    }

    private static List<double[]> points(JsonNode a) {
        List<double[]> out = new ArrayList<>(a.size());
        a.forEach(n -> out.add(new double[]{n.get(0).asDouble(), n.get(1).asDouble()}));
        return out;
    }

    private static double[] doubles(JsonNode a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).asDouble();
        return out;
    }

    private static int[] ints(JsonNode a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).asInt();
        return out;
    }
}
