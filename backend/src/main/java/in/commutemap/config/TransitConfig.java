package in.commutemap.config;

import in.commutemap.data.DataLoader;
import in.commutemap.data.TransitData;
import in.commutemap.places.PlaceScores;
import in.commutemap.routing.Router;
import in.commutemap.routing.TransitGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Loads the transit data once at startup and builds the in-memory graph. */
@Configuration
public class TransitConfig {

    private static final Logger log = LoggerFactory.getLogger(TransitConfig.class);

    @Bean
    TransitData transitData(CommuteProperties props) {
        long t0 = System.nanoTime();
        TransitData d = DataLoader.load(props.dataPath());
        log.info("Loaded {} metro stations, {} bus stops, {} bus route patterns and {} wards from {} in {} ms",
                d.stations().size(), d.busStops().size(), d.patterns().size(), d.wards().size(),
                props.dataPath().toAbsolutePath().normalize(), (System.nanoTime() - t0) / 1_000_000);
        return d;
    }

    @Bean
    TransitGraph transitGraph(TransitData data) {
        long t0 = System.nanoTime();
        TransitGraph g = new TransitGraph(data);
        log.info("Built the transit graph ({} bus stops + {} metro states) in {} ms",
                g.busStopCount(), g.metroStateCount(), (System.nanoTime() - t0) / 1_000_000);
        return g;
    }

    @Bean
    PlaceScores placeScores(TransitData data) {
        return new PlaceScores(data);
    }

    @Bean
    Router router(TransitGraph graph, CommuteProperties props) {
        log.info("Ride fix is {}", props.rideFix() ? "on" : "off (results match the prototype exactly)");
        return new Router(graph, props.rideFix());
    }

    @Bean
    WebMvcConfigurer cors(CommuteProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(props.allowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET")
                        .maxAge(3600);
            }
        };
    }
}
