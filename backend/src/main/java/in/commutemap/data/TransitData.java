package in.commutemap.data;

import java.util.List;
import java.util.Map;

/**
 * Everything read from {@code data/}, as plain immutable values. {@link in.commutemap.routing.TransitGraph}
 * turns this into the search structures.
 */
public record TransitData(
        Feeds feeds,
        List<Station> stations,
        Map<String, Line> lines,
        List<Edge> edges,
        Map<String, Map<String, int[]>> headways,
        List<Ward> wards,
        List<double[][]> outline,
        BusStops busStops,
        List<Pattern> patterns,
        Places places) {

    public record Feeds(String metro, String bus, String wards, String places) {}

    /** A metro station. {@code lines} are line ids such as {@code PURPLE}, sorted. */
    public record Station(String id, String name, double lat, double lon, List<String> lines) {}

    /** A metro line; {@code path} is a list of [lat, lon] for drawing. */
    public record Line(String id, String name, String color, String longName, List<double[]> path) {}

    /** Ride time between two neighbouring stations on one line, in seconds (timetable median). */
    public record Edge(String line, String from, String to, int seconds) {}

    /**
     * A ward. {@code rings} are outlines of [lat, lon] points. {@code metroStation}/{@code metroMetres}
     * list the stations within 7 km of the ward's nearest edge (0 m if inside).
     */
    public record Ward(int index, String no, String name, double[] centre, double areaKm2,
                       List<double[][]> rings, int[] metroStation, int[] metroMetres) {}

    public record BusStops(double[] lat, double[] lon, String[] name) {
        public int size() {
            return name.length;
        }
    }

    /**
     * One exact sequence of bus stops, with every route that runs it merged.
     * Gaps are minutes between buses (0 = no buses in that window). {@code cumTenths} is the time since
     * the first stop in tenths of a minute.
     */
    public record Pattern(String label, List<String> routes, double gapMorning, double gapEvening,
                          int[] stops, int[] cumTenths) {}

    /** OpenStreetMap place counts per ward. {@code count[ward][type]}, {@code nearest10m[ward][type]}. */
    public record Places(String source, String base, List<Category> categories, int[][] count, int[][] nearest10m) {}

    public record Category(String key, String label, String shortLabel, String one) {}
}
