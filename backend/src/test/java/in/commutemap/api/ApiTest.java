package in.commutemap.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    private static final String ITPL = "lat=12.9855&lon=77.7368";

    @Autowired
    MockMvc mvc;

    @Test
    void reachFromItpl() throws Exception {
        mvc.perform(get("/api/reach?" + ITPL + "&limit=45&mode=walk&leg=15&when=morning&net=all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(22))
                .andExpect(jsonPath("$.query.net").value("all"))
                .andExpect(jsonPath("$.wards[0].minutes").value(lessThanOrEqualTo(45)))
                .andExpect(jsonPath("$.wards[0].via").isArray())
                .andExpect(jsonPath("$.nearestStation.name").value("Pattandur Agrahara"))
                .andExpect(jsonPath("$.nearestBusStop.name").isString());
        mvc.perform(get("/api/reach?" + ITPL + "&net=metro"))
                .andExpect(jsonPath("$.reachable").value(17))
                .andExpect(jsonPath("$.nearestBusStop").value(nullValue()));
    }

    @Test
    void onlyTheOfficeIsRequired() throws Exception {
        mvc.perform(get("/api/reach?" + ITPL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.limit").value(45))
                .andExpect(jsonPath("$.query.mode").value("walk"))
                .andExpect(jsonPath("$.query.leg").value(15))
                .andExpect(jsonPath("$.query.when").value("morning"))
                .andExpect(jsonPath("$.reachable").value(22));
    }

    @Test
    void badParametersAreExplained() throws Exception {
        mvc.perform(get("/api/reach?lon=77.7&limit=200&mode=bike&when=night"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.lat").value("is required"))
                .andExpect(jsonPath("$.errors.limit").value("must be 15 to 90 minutes"))
                .andExpect(jsonPath("$.errors.mode").value("must be walk or auto"))
                .andExpect(jsonPath("$.errors.when").value("must be morning or evening"));
        mvc.perform(get("/api/reach?lat=abc&lon=77.7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lat").value("must be a number"));
        mvc.perform(get("/api/reach?lat=28.61&lon=77.2"))   // Delhi
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lat").value(containsString("Bengaluru")));
    }

    @Test
    void tripToAWard() throws Exception {
        MvcResult reach = mvc.perform(get("/api/reach?" + ITPL)).andReturn();
        int ward = com.jayway.jsonpath.JsonPath.read(reach.getResponse().getContentAsString(), "$.wards[3].ward");
        mvc.perform(get("/api/trip?" + ITPL + "&ward=" + ward))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ward").value(ward))
                .andExpect(jsonPath("$.trip.steps[0].type").value(anyOf(is("ACCESS"), is("DIRECT"))))
                .andExpect(jsonPath("$.trip.steps[-1:].type").value(anyOf(hasItem("FINAL"), hasItem("DIRECT"))))
                .andExpect(jsonPath("$.trip.steps[0].text").isString());
    }

    @Test
    void tripToAnUnreachableWardIsEmpty() throws Exception {
        // ward 0 (Kempegowda, far north) is not reachable from ITPL in 15 min
        mvc.perform(get("/api/trip?" + ITPL + "&limit=15&ward=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wardName").isString())
                .andExpect(jsonPath("$.trip").value(nullValue()));
    }

    @Test
    void tripParametersAreChecked() throws Exception {
        mvc.perform(get("/api/trip?" + ITPL + "&ward=999"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/trip?" + ITPL + "&ward=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ward").value("must be 0 or more"));
        mvc.perform(get("/api/trip?" + ITPL + "&limit=5&ward=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limit").value("must be 15 to 90 minutes"));
    }

    @Test
    void wardsAreGeoJsonAndCacheable() throws Exception {
        MvcResult r = mvc.perform(get("/api/wards"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(243))
                .andExpect(jsonPath("$.features[0].geometry.type").value("MultiPolygon"))
                .andExpect(jsonPath("$.features[0].properties.count.length()").value(7))
                .andExpect(jsonPath("$.features[0].properties.scores.length()").value(7))
                .andExpect(jsonPath("$.features[0].properties.score").isNumber())
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andReturn();
        String etag = r.getResponse().getHeader("ETag");
        mvc.perform(get("/api/wards").header("If-None-Match", etag)).andExpect(status().isNotModified());
    }

    @Test
    void metaListsOfficesAndLines() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offices.length()").value(11))
                .andExpect(jsonPath("$.offices[0].name").value("ITPL, Whitefield"))
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.stations.length()").value(83))
                .andExpect(jsonPath("$.feeds.bus").value(containsString("BMTC")));
    }

    @Test
    void googleNearbyIsOffWithoutAKey() throws Exception {
        mvc.perform(get("/api/places/nearby?lat=12.97&lon=77.64&type=gym"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("not set up")));
        mvc.perform(get("/api/meta")).andExpect(jsonPath("$.googlePlaces").value(false));
    }

    @Test
    void browsersFromTheFrontendMayCall() throws Exception {
        mvc.perform(get("/api/meta").header("Origin", "http://localhost:5180"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5180"));
        mvc.perform(get("/api/meta").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }
}
