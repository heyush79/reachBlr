package in.commutemap.trip;

import in.commutemap.data.TransitData;
import in.commutemap.data.TransitData.Line;
import in.commutemap.data.TransitData.Station;
import in.commutemap.routing.Search;
import in.commutemap.routing.Search.Kind;
import in.commutemap.routing.Search.WardResult;
import in.commutemap.routing.SearchQuery;
import in.commutemap.trip.Trip.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Traces a search result back from the ward to the office and writes it as steps.
 * A port of {@code steps()} in the prototype: same grouping and same wording.
 */
public final class TripBuilder {

    private TripBuilder() {}

    /** The trip to one ward, or {@code null} if no trip fits the limits. */
    public static Trip build(Search s, int wardIndex) {
        WardResult r = s.ward(wardIndex);
        if (r == null) return null;
        TransitData d = s.graph().data();
        SearchQuery q = s.query();
        String word = q.access() == SearchQuery.Access.WALK ? "Walk" : "Auto or cab";
        String wardName = d.wards().get(wardIndex).name();
        boolean over = r.minutes() > q.limit();
        if (r.direct()) {
            Step only = new Step(StepType.DIRECT, word + " " + round(r.lastLeg()) + " min straight there, no bus or metro needed",
                    round(r.lastLeg()), null, wardName, null, null, null,
                    List.of(new double[]{q.lat(), q.lon()}, d.wards().get(wardIndex).centre()));
            return new Trip(wardIndex, wardName, r.minutes(), over, true, List.of(), List.of(only));
        }
        Trace t = new Trace(s);
        List<Integer> chain = new ArrayList<>();
        for (int v = r.node(); v >= 0; v = t.from(v)) {
            chain.addFirst(v);
            if (t.kind(v) == Kind.START_METRO || t.kind(v) == Kind.START_BUS) break;
        }
        List<Step> steps = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        double[] office = {q.lat(), q.lon()};
        int i = 0;
        while (i < chain.size()) {
            int v = chain.get(i);
            Kind k = t.kind(v);
            if (k == Kind.START_METRO || k == Kind.START_BUS) {
                int m = round(t.val(v));
                steps.add(new Step(StepType.ACCESS, word + " " + m + " min to " + t.name(v), m, null, t.name(v), null,
                        null, null, List.of(office, t.ll(v))));
            }
            if (k == Kind.WALK) {
                int m = round(t.val(v));
                steps.add(new Step(StepType.WALK, "Walk " + m + " min to " + t.name(v), m, null, t.name(v), null,
                        null, null, List.of(t.ll(t.from(v)), t.ll(v))));
            }
            if (k == Kind.EXIT) {
                int m = round(t.val(v));
                steps.add(new Step(StepType.EXIT_WALK, "Leave the station and walk " + m + " min to " + t.name(v), m,
                        null, t.name(v), null, null, null, List.of(t.ll(t.from(v)), t.ll(v))));
            }
            if (k == Kind.BUS) {
                String label = t.patternLabel(v);
                int others = t.routes(v) - 1, stops = t.j(v) - t.i(v), wait = round(t.val(v)), ride = round(t.val2(v));
                used.add("Bus " + label.split(" / ")[0]);
                String text = "Wait about " + wait + " min, then bus " + label
                        + (others > 0 ? " or " + others + " other route" + (others > 1 ? "s" : "") : "")
                        + " to " + t.name(v) + ", " + stops + " stop" + (stops > 1 ? "s" : "") + ", about " + ride + " min";
                steps.add(new Step(StepType.BUS, text, ride, wait, t.name(v), stops, null, new BusRide(label, others),
                        t.busPath(v)));
            }
            if (k == Kind.START_METRO || k == Kind.ENTER || k == Kind.CHANGE) {
                Line line = t.line(v);
                if (k == Kind.ENTER) {
                    int m = round(t.val(v));
                    steps.add(new Step(StepType.WALK, "Walk " + m + " min to " + t.name(v), m, null, t.name(v), null,
                            null, null, List.of(t.ll(t.from(v)), t.ll(v))));
                }
                int j = i + 1;
                double mins = 0;
                List<double[]> pts = new ArrayList<>();
                pts.add(t.ll(v));
                while (j < chain.size() && t.kind(chain.get(j)) == Kind.RIDE) {
                    mins += t.val(chain.get(j));
                    pts.add(t.ll(chain.get(j)));
                    j++;
                }
                if (j > i + 1) {
                    int w = round(k == Kind.CHANGE ? t.val(v) : t.val2(v));
                    int end = chain.get(j - 1), stops = j - i - 1, rideMin = round(mins);
                    String pre = k == Kind.CHANGE ? "Change at " + t.name(v) + ", about " + w + " min. "
                            : "Enter and wait about " + w + " min. ";
                    String text = pre + line.name() + " Line to " + t.name(end) + ", " + stops + " stop"
                            + (stops > 1 ? "s" : "") + ", " + rideMin + " min";
                    steps.add(new Step(StepType.METRO, text, rideMin, w, t.name(end), stops,
                            new MetroLine(line.id(), line.name(), line.color()), null, pts));
                    used.add(line.name() + " Line");
                    i = j;
                    continue;
                }
            }
            i++;
        }
        String endText = r.byMetro() ? "Leave the station, then " + word.toLowerCase() : word;
        steps.add(new Step(StepType.FINAL, endText + " " + round(r.lastLeg()) + " min into the nearest part of the ward",
                round(r.lastLeg()), null, wardName, null, null, null, List.of()));
        return new Trip(wardIndex, wardName, r.minutes(), over, false, List.copyOf(used), steps);
    }

    /** The lines a trip uses, without building its steps' text and shapes. */
    public static List<String> via(Search s, int wardIndex) {
        Trip t = build(s, wardIndex);
        return t == null ? List.of() : t.via();
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    /** Read-only view of how each node was reached, with names and positions. */
    private static final class Trace {
        private final Search s;
        private final TransitData d;

        Trace(Search s) {
            this.s = s;
            this.d = s.graph().data();
        }

        Kind kind(int v) {
            return s.kindOf(v);
        }

        int from(int v) {
            return s.fromOf(v);
        }

        double val(int v) {
            return s.valueOf(v);
        }

        double val2(int v) {
            return s.value2Of(v);
        }

        int i(int v) {
            return s.boardPosition(v);
        }

        int j(int v) {
            return s.alightPosition(v);
        }

        int routes(int v) {
            return s.routesCounted(v);
        }

        String patternLabel(int v) {
            return s.patternLabel(v);
        }

        boolean isBusStop(int v) {
            return s.isBusStop(v);
        }

        Station station(int v) {
            return d.stations().get(s.stationOf(v));
        }

        Line line(int v) {
            return d.lines().get(s.lineOf(v));
        }

        String name(int v) {
            return isBusStop(v) ? d.busStops().name()[s.busStopOf(v)] + " bus stop" : station(v).name();
        }

        double[] ll(int v) {
            if (isBusStop(v)) return new double[]{d.busStops().lat()[s.busStopOf(v)], d.busStops().lon()[s.busStopOf(v)]};
            Station st = station(v);
            return new double[]{st.lat(), st.lon()};
        }

        List<double[]> busPath(int v) {
            int[] stops = s.patternStops(v);
            List<double[]> pts = new ArrayList<>();
            for (int k = i(v); k <= j(v); k++) pts.add(new double[]{d.busStops().lat()[stops[k]], d.busStops().lon()[stops[k]]});
            return pts;
        }
    }
}
