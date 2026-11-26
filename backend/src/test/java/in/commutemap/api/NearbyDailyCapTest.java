package in.commutemap.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The site-wide daily cap holds however many different visitors ask. Google is pointed at a closed port:
 * the two allowed lookups fail (502), and the third is refused before any call is made (429).
 */
@SpringBootTest(properties = {"commute.google.api-key=k", "commute.google.global-per-day=2",
        "commute.google.base-url=http://127.0.0.1:9"})
@AutoConfigureMockMvc
class NearbyDailyCapTest {

    @Autowired
    MockMvc mvc;

    @Test
    void dailyCapForTheWholeSite() throws Exception {
        for (String ip : new String[]{"10.1.0.1", "10.1.0.2"}) {
            mvc.perform(get("/api/places/nearby?lat=12.97&lon=77.64&type=gym").with(r -> {
                r.setRemoteAddr(ip);
                return r;
            })).andExpect(status().isBadGateway());
        }
        mvc.perform(get("/api/places/nearby?lat=12.97&lon=77.64&type=gym").with(r -> {
                    r.setRemoteAddr("10.1.0.3");
                    return r;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(containsString("today's Google lookups")));
    }
}
