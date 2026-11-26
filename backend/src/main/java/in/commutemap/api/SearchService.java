package in.commutemap.api;

import in.commutemap.config.CommuteProperties;
import in.commutemap.routing.Router;
import in.commutemap.routing.Search;
import in.commutemap.routing.SearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs searches and keeps the most recent ones, so asking for a ward's trip right after the search
 * does not search again.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final Router router;
    private final Map<SearchQuery, Search> recent;

    public SearchService(Router router, CommuteProperties props) {
        this.router = router;
        int size = Math.max(1, props.searchCacheSize());
        this.recent = new LinkedHashMap<>(size * 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<SearchQuery, Search> eldest) {
                return size() > size;
            }
        };
    }

    public Search search(SearchQuery query) {
        SearchQuery q = query.normalised();
        synchronized (recent) {
            Search hit = recent.get(q);
            if (hit != null) return hit;
        }
        Search s = router.search(q);   // outside the lock: searches can run in parallel
        log.debug("search {} -> {} wards in {} ms", q, s.reachableCount(), String.format("%.1f", s.millis()));
        synchronized (recent) {
            recent.put(q, s);
        }
        return s;
    }
}
