package in.commutemap.routing;

/**
 * One commute search.
 *
 * @param lat     office latitude
 * @param lon     office longitude
 * @param limit   longest commute accepted, minutes
 * @param access  how you get to the first stop and from the last one
 * @param leg     longest trip to or from a stop, minutes
 * @param when    weekday morning or evening (8-10 pm)
 * @param network metro only, or metro and bus
 */
public record SearchQuery(double lat, double lon, int limit, Access access, int leg, TimeOfDay when, Network network) {

    public enum Access {
        WALK, AUTO;

        /** Minutes to cover {@code metres} to or from a stop. */
        public double minutes(double metres) {
            return this == WALK ? Geo.walkMin(metres) : Geo.autoMin(metres);
        }
    }

    public enum TimeOfDay {
        MORNING, EVENING
    }

    public enum Network {
        METRO, ALL
    }

    /** Same search with the office rounded to 5 decimals (about 1 m), so near-identical requests share a cache entry. */
    public SearchQuery normalised() {
        return new SearchQuery(Math.round(lat * 1e5) / 1e5, Math.round(lon * 1e5) / 1e5, limit, access, leg, when, network);
    }
}
