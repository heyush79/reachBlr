package in.commutemap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The Google Places proxy, against a fake Google server on a local port. */
@SpringBootTest(properties = {"commute.google.api-key=test-key-123", "commute.google.per-ip-per-minute=3",
        "commute.google.global-per-day=1000"})
@AutoConfigureMockMvc
class NearbyPlacesTest {

    static final HttpServer GOOGLE;
    static final AtomicInteger CALLS = new AtomicInteger();
    static volatile String method, path, key, mask, body;
    static volatile int status = 200;
    static volatile String answer;

    static {
        try {
            GOOGLE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        GOOGLE.createContext("/", ex -> {
            CALLS.incrementAndGet();
            method = ex.getRequestMethod();
            path = ex.getRequestURI().getPath();
            key = ex.getRequestHeaders().getFirst("X-Goog-Api-Key");
            mask = ex.getRequestHeaders().getFirst("X-Goog-FieldMask");
            body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = answer.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        GOOGLE.start();
    }

    @DynamicPropertySource
    static void googleAddress(DynamicPropertyRegistry r) {
        r.add("commute.google.base-url", () -> "http://127.0.0.1:" + GOOGLE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        GOOGLE.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        status = 200;
        answer = """
                {"places":[
                  {"id":"ChIJ-1","displayName":{"text":"Iron Gym","languageCode":"en"},
                   "shortFormattedAddress":"12th Main, Indiranagar","location":{"latitude":12.9719,"longitude":77.6412},
                   "googleMapsUri":"https://maps.google.com/?cid=1",
                   "attributions":[{"provider":"Some Data Co","providerUri":"https://data.example"}]},
                  {"id":"ChIJ-2","displayName":{"text":"Fit Hub"},"location":{"latitude":12.975,"longitude":77.645},
                   "googleMapsUri":"https://maps.google.com/?cid=2"}
                ]}""";
    }

    private static MockHttpServletRequestBuilder nearby(String query, String ip) {
        return get("/api/places/nearby?" + query).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    @Test
    void asksGoogleForCheapFieldsAndShapesTheAnswer() throws Exception {
        String reply = mvc.perform(nearby("lat=12.9716&lon=77.6412&type=gym", "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.attribution").value("Google Maps"))
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].name").value("Iron Gym"))
                .andExpect(jsonPath("$.places[0].address").value("12th Main, Indiranagar"))
                .andExpect(jsonPath("$.places[0].distanceMetres").value(33))
                .andExpect(jsonPath("$.places[0].googleMapsUri").value("https://maps.google.com/?cid=1"))
                .andExpect(jsonPath("$.places[0].attributions[0].provider").value("Some Data Co"))
                .andExpect(jsonPath("$.places[1].attributions").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertEquals("POST", method);
        assertEquals("/v1/places:searchNearby", path);
        assertEquals("test-key-123", key);
        assertEquals("places.id,places.displayName,places.shortFormattedAddress,places.location,places.googleMapsUri,"
                + "places.attributions", mask, "only Nearby Search Pro fields");
        JsonNode b = new ObjectMapper().readTree(body);
        assertEquals("[\"gym\",\"fitness_center\"]", b.get("includedTypes").toString());
        assertEquals(10, b.get("maxResultCount").asInt());
        assertEquals("DISTANCE", b.get("rankPreference").asText());
        assertEquals(1000.0, b.at("/locationRestriction/circle/radius").asDouble());
        assertEquals(12.9716, b.at("/locationRestriction/circle/center/latitude").asDouble());
        assertFalse(reply.contains("test-key-123"), "the key never reaches the browser");
    }

    @Test
    void noResults() throws Exception {
        answer = "{}";
        mvc.perform(nearby("lat=12.9716&lon=77.6412&type=park", "10.0.0.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places").isEmpty());
    }

    @Test
    void eachVisitorGetsAFewLookupsAMinute() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(nearby("lat=12.97&lon=77.64&type=food", "10.0.0.2")).andExpect(status().isOk());
        }
        int before = CALLS.get();
        mvc.perform(nearby("lat=12.97&lon=77.64&type=food", "10.0.0.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value(containsString("try again in a minute")));
        assertEquals(before, CALLS.get(), "a refused lookup never reaches Google");
        mvc.perform(nearby("lat=12.97&lon=77.64&type=food", "10.0.0.9")).andExpect(status().isOk());
    }

    @Test
    void googleErrorsBecomeA502WithoutDetails() throws Exception {
        status = 403;
        answer = "{\"error\":{\"code\":403,\"message\":\"API key test-key-123 not valid\"}}";
        String reply = mvc.perform(nearby("lat=12.97&lon=77.64&type=school", "10.0.0.4"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Google Places did not answer. Please try again later."))
                .andReturn().getResponse().getContentAsString();
        assertFalse(reply.contains("test-key-123"));
    }

    @Test
    void badParameters() throws Exception {
        mvc.perform(nearby("lat=12.97&lon=77.64&type=bar", "10.0.0.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").value(startsWith("must be gym")));
        mvc.perform(nearby("lat=28.6&lon=77.2&type=gym", "10.0.0.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lat").value(containsString("Bengaluru")));
    }

    @Test
    void metaSaysTheFeatureIsOn() throws Exception {
        mvc.perform(get("/api/meta")).andExpect(jsonPath("$.googlePlaces").value(true));
    }
}
