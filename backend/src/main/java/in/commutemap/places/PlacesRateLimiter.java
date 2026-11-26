package in.commutemap.places;

import in.commutemap.config.GoogleProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps a public site from running up the Google bill, with two token buckets (Bucket4j):
 * <ul>
 *   <li>per visitor (IP address): a few lookups per minute, refilled smoothly;</li>
 *   <li>for the whole site: a fixed number per day, a hard cap however many visitors there are.</li>
 * </ul>
 * Set a daily quota on the key in Google Cloud Console as well, as a second guard.
 */
@Component
public class PlacesRateLimiter {

    private static final int MAX_TRACKED_IPS = 10_000;

    private final int perIpPerMinute;
    private final Bucket global;
    private final Map<String, Bucket> perIp = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED_IPS;
        }
    };

    public PlacesRateLimiter(GoogleProperties props) {
        this.perIpPerMinute = Math.max(1, props.perIpPerMinute());
        int perDay = Math.max(1, props.globalPerDay());
        this.global = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perDay).refillIntervally(perDay, Duration.ofDays(1)).build())
                .build();
    }

    /** Uses up one lookup for this visitor and for the site, or throws {@link RateLimitedException}. */
    public void take(String clientIp) {
        Bucket mine;
        synchronized (perIp) {
            mine = perIp.computeIfAbsent(clientIp, ip -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(perIpPerMinute)
                            .refillGreedy(perIpPerMinute, Duration.ofMinutes(1)).build())
                    .build());
        }
        ConsumptionProbe p = mine.tryConsumeAndReturnRemaining(1);
        if (!p.isConsumed()) {
            throw new RateLimitedException(seconds(p), "Too many lookups from your connection. Please try again in a minute.");
        }
        ConsumptionProbe g = global.tryConsumeAndReturnRemaining(1);
        if (!g.isConsumed()) {
            throw new RateLimitedException(seconds(g), "The site has used today's Google lookups. Please try again tomorrow.");
        }
    }

    private static long seconds(ConsumptionProbe p) {
        return Math.max(1, (p.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
    }

    public static final class RateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitedException(long retryAfterSeconds, String message) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
