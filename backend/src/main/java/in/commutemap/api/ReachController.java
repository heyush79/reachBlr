package in.commutemap.api;

import in.commutemap.api.ApiDtos.*;
import in.commutemap.data.TransitData;
import in.commutemap.routing.Geo;
import in.commutemap.routing.Search;
import in.commutemap.routing.SearchQuery;
import in.commutemap.trip.Trip;
import in.commutemap.trip.TripBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Commute searches: which wards you can reach, and how. */
@RestController
@RequestMapping("/api")
public class ReachController {

    private final SearchService searches;
    private final TransitData data;

    public ReachController(SearchService searches, TransitData data) {
        this.searches = searches;
        this.data = data;
    }

    /** Minutes to every ward you can reach, quickest first. */
    @GetMapping("/reach")
    public ReachResponse reach(@Valid @ModelAttribute SearchParams params) {
        SearchQuery q = params.toQuery();
        Search s = searches.search(q);
        List<WardTime> wards = s.wards().stream()
                .filter(r -> r != null)
                .sorted(Comparator.comparingInt(Search.WardResult::minutes).thenComparingInt(Search.WardResult::ward))
                .map(r -> new WardTime(r.ward(), data.wards().get(r.ward()).name(), r.minutes(),
                        r.minutes() > q.limit(), r.direct(), TripBuilder.via(s, r.ward())))
                .toList();
        Search.NearestStation ns = s.nearestStation();
        Search.NearestBusStop nb = s.nearestBusStop();
        return new ReachResponse(echo(s.query()), s.reachableCount(), wards,
                new NearestStation(ns.name(), round1(ns.minutes()), (int) Math.round(ns.metres()), ns.minutes() <= q.leg()),
                nb == null ? null : new NearestBusStop(nb.name(), (int) Math.round(nb.metres()),
                        (int) Math.max(1, Math.round(Geo.walkMin(nb.metres()))), nb.routeNumbers()),
                round1(s.millis()));
    }

    /** The step-by-step trip to one ward. */
    @GetMapping("/trip")
    public TripResponse trip(@Valid @ModelAttribute SearchParams params,
                             @RequestParam @NotNull @Min(value = 0, message = "must be 0 or more") Integer ward) {
        if (ward >= data.wards().size()) throw new IllegalArgumentException("ward must be 0 to " + (data.wards().size() - 1));
        Search s = searches.search(params.toQuery());
        Trip t = TripBuilder.build(s, ward);
        return new TripResponse(ward, data.wards().get(ward).name(), t);
    }

    private static Query echo(SearchQuery q) {
        return new Query(q.lat(), q.lon(), q.limit(), lower(q.access()), q.leg(), lower(q.when()), lower(q.network()));
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
