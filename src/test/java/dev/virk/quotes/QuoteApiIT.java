package dev.virk.quotes;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class QuoteApiIT extends MySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    static String body(String zone, String kg, String level) {
        String lvl = level == null ? "" : ", \"serviceLevel\": \"" + level + "\"";
        return "{\"customerEmail\": \"buyer@example.com\", \"destinationZone\": \"" + zone
                + "\", \"weightKg\": " + kg + lvl + "}";
    }

    long createQuote(String zone, String kg, String level) throws Exception {
        String res = mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body(zone, kg, level)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(res);
        return n.get("id").asLong();
    }

    @Test
    void creatingAQuotePricesItFromTableV1() throws Exception {
        // EU-W up to 10 kg: 1200 + 4*90 = 1560, +8% fuel = 1684.8 -> 1685
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body("EU-W", "4", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/quotes/")))
                .andExpect(jsonPath("$.priceCents").value(1685))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.rateTableVersion").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.serviceLevel").value("STANDARD"));
    }

    @Test
    void expressCostsMoreThanStandard() throws Exception {
        // 1560 * 1.5 = 2340, * 1.08 = 2527.2 -> 2527
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body("EU-W", "4", "EXPRESS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceCents").value(2527));
    }

    @Test
    void theQuoteWindowIsThirtyMinutes() throws Exception {
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body("UK", "12.5", null)))
                .andExpect(jsonPath("$.createdAt").value("2026-06-01T12:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-06-01T12:30:00Z"));
    }

    @Test
    void aBadBodyGetsProblemJsonWithEveryBadField() throws Exception {
        String bad = "{\"customerEmail\": \"nope\", \"destinationZone\": \"eu w\", \"weightKg\": -2}";
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:quote-service:problem:validation"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].field").value("customerEmail"))
                .andExpect(jsonPath("$.errors[1].field").value("destinationZone"))
                .andExpect(jsonPath("$.errors[2].field").value("weightKg"));
    }

    @Test
    void aMissingWeightIsRefused() throws Exception {
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerEmail\": \"a@b.co\", \"destinationZone\": \"UK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("weightKg"));
    }

    @Test
    void anUnknownServiceLevelIsAProblemNotAStackTrace() throws Exception {
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body("UK", "2", "TELEPORT")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void malformedJsonIsAProblem() throws Exception {
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aParcelNoBandCoversIsUnprocessable() throws Exception {
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body("UK", "900", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:quote-service:problem:no-rate"));
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body("MARS", "5", null)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void anUnknownQuoteIs404Problem() throws Exception {
        mvc.perform(get("/api/quotes/987654"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:quote-service:problem:quote-not-found"));
        mvc.perform(post("/api/quotes/987654/accept")).andExpect(status().isNotFound());
    }

    @Test
    void aNonNumericIdIs400() throws Exception {
        mvc.perform(get("/api/quotes/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void acceptingMovesTheQuoteToAccepted() throws Exception {
        long id = createQuote("EU-E", "20", null);
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(post("/api/quotes/" + id + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedAt").value("2026-06-01T12:05:00Z"));
        mvc.perform(get("/api/quotes/" + id)).andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void acceptingTwiceIsAConflict() throws Exception {
        long id = createQuote("EU-E", "20", null);
        mvc.perform(post("/api/quotes/" + id + "/accept")).andExpect(status().isOk());
        mvc.perform(post("/api/quotes/" + id + "/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:quote-service:problem:quote-already-accepted"));
    }

    @Test
    void acceptingAfterTheWindowIsAConflictAndMarksItExpired() throws Exception {
        long id = createQuote("EU-E", "20", null);
        clock.advance(Duration.ofMinutes(30));
        mvc.perform(post("/api/quotes/" + id + "/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:quote-service:problem:quote-expired"));
        mvc.perform(get("/api/quotes/" + id)).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void oneSecondBeforeTheDeadlineStillAccepts() throws Exception {
        long id = createQuote("EU-E", "20", null);
        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        mvc.perform(post("/api/quotes/" + id + "/accept")).andExpect(status().isOk());
    }

    @Test
    void theSweepExpiresOnlyWhatIsDue() throws Exception {
        long early = createQuote("UK", "5", null);
        clock.advance(Duration.ofMinutes(20));
        long late = createQuote("UK", "5", null);
        clock.advance(Duration.ofMinutes(15));   // early is 35 min old, late is 15
        mvc.perform(post("/api/quotes/expire")).andExpect(jsonPath("$.expired").value(1));
        mvc.perform(get("/api/quotes/" + early)).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/quotes/" + late)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void theSweepLeavesAcceptedQuotesAlone() throws Exception {
        long id = createQuote("UK", "5", null);
        mvc.perform(post("/api/quotes/" + id + "/accept")).andExpect(status().isOk());
        clock.advance(Duration.ofHours(2));
        mvc.perform(post("/api/quotes/expire")).andExpect(jsonPath("$.expired").value(0));
        mvc.perform(get("/api/quotes/" + id)).andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void theSweepIsIdempotent() throws Exception {
        createQuote("UK", "5", null);
        clock.advance(Duration.ofHours(1));
        mvc.perform(post("/api/quotes/expire")).andExpect(jsonPath("$.expired").value(1));
        mvc.perform(post("/api/quotes/expire")).andExpect(jsonPath("$.expired").value(0));
    }

    @Test
    void anExpiredQuoteStaysExpiredAfterTheSweep() throws Exception {
        long id = createQuote("UK", "5", null);
        clock.advance(Duration.ofHours(1));
        mvc.perform(post("/api/quotes/expire"));
        mvc.perform(post("/api/quotes/" + id + "/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:quote-service:problem:quote-expired"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        long a = createQuote("UK", "5", null);
        createQuote("UK", "6", null);
        mvc.perform(post("/api/quotes/" + a + "/accept")).andExpect(status().isOk());
        mvc.perform(get("/api/quotes?status=ACCEPTED")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/quotes?status=OPEN")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/quotes")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void anUnknownStatusFilterIs400() throws Exception {
        mvc.perform(get("/api/quotes?status=LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("status")));
    }
}
