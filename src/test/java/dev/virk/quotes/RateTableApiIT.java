package dev.virk.quotes;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class RateTableApiIT extends MySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** One zone, one band, flat 1000 + 100 per kg, no surcharge. */
    static String table(String effectiveFrom, int perKg) {
        return """
                {"effectiveFrom": "%s", "currency": "EUR", "fuelSurchargeBps": 0, "expressMultiplierBps": 15000,
                 "minChargeCents": 0,
                 "bands": [{"zone": "EU-W", "upToKg": 100, "baseCents": 1000, "perKgCents": %d}]}
                """.formatted(effectiveFrom, perKg);
    }

    @Test
    void theSeededTableIsVersionOne() throws Exception {
        mvc.perform(get("/api/rate-tables")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].bands", hasSize(9)));
    }

    @Test
    void creatingATableTakesTheNextVersion() throws Exception {
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-07-01T00:00:00Z", 100)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-08-01T00:00:00Z", 100)))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void aTableCanBeReadBackByVersion() throws Exception {
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-07-01T00:00:00Z", 123)));
        mvc.perform(get("/api/rate-tables/2")).andExpect(jsonPath("$.bands[0].perKgCents").value(123));
        mvc.perform(get("/api/rate-tables/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aFutureTableIsNotInForceYet() throws Exception {
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-09-01T00:00:00Z", 500)));
        mvc.perform(get("/api/rate-tables/active")).andExpect(jsonPath("$.version").value(1));
        clock.set(java.time.Instant.parse("2026-09-01T00:00:00Z"));
        mvc.perform(get("/api/rate-tables/active")).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void whenTwoTablesShareADateTheHigherVersionWins() throws Exception {
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-03-01T00:00:00Z", 100)));
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-03-01T00:00:00Z", 200)));
        mvc.perform(get("/api/rate-tables/active")).andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void twentyKilosToEuWPricesFromSeedBandTwo() throws Exception {
        String q = "{\"customerEmail\": \"a@b.co\", \"destinationZone\": \"EU-W\", \"weightKg\": 20}";
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(q))
                .andExpect(jsonPath("$.priceCents").value(3672)); // (2000 + 20*70) * 1.08
    }

    @Test
    void newQuotesUseTheNewTableAndOldOnesDoNot() throws Exception {
        String q = "{\"customerEmail\": \"a@b.co\", \"destinationZone\": \"EU-W\", \"weightKg\": 2}";
        String first = mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(q))
                .andReturn().getResponse().getContentAsString();
        long firstId = json.readTree(first).get("id").asLong();

        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-06-01T12:00:00Z", 100)));
        clock.advance(Duration.ofSeconds(1));
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(q))
                .andExpect(jsonPath("$.rateTableVersion").value(2))
                .andExpect(jsonPath("$.priceCents").value(1200)); // 1000 + 2*100, no surcharge
        mvc.perform(get("/api/quotes/" + firstId))
                .andExpect(jsonPath("$.rateTableVersion").value(1));
    }

    @Test
    void anOpenQuoteIsAcceptedAtItsOriginalPriceAfterANewTable() throws Exception {
        String q = "{\"customerEmail\": \"a@b.co\", \"destinationZone\": \"EU-W\", \"weightKg\": 2}";
        String first = mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(q))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(first).get("id").asLong();
        long price = json.readTree(first).get("priceCents").asLong();
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(table("2026-06-01T12:00:00Z", 900)));
        mvc.perform(post("/api/quotes/" + id + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceCents").value(price))
                .andExpect(jsonPath("$.rateTableVersion").value(1));
    }

    @Test
    void duplicateBandsInOneZoneAreRefused() throws Exception {
        String dup = """
                {"effectiveFrom": "2026-07-01T00:00:00Z", "currency": "EUR", "fuelSurchargeBps": 0,
                 "expressMultiplierBps": 15000, "minChargeCents": 0,
                 "bands": [{"zone": "UK", "upToKg": 10, "baseCents": 1, "perKgCents": 1},
                           {"zone": "UK", "upToKg": 10.00, "baseCents": 2, "perKgCents": 2}]}
                """;
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(dup))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:quote-service:problem:invalid-rate-table"));
        mvc.perform(get("/api/rate-tables")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void aTableWithNoBandsIsRefused() throws Exception {
        String none = """
                {"effectiveFrom": "2026-07-01T00:00:00Z", "currency": "EUR", "fuelSurchargeBps": 0,
                 "expressMultiplierBps": 15000, "minChargeCents": 0, "bands": []}
                """;
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(none))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bands"));
    }

    @Test
    void bandFieldsAreValidated() throws Exception {
        String bad = """
                {"effectiveFrom": "2026-07-01T00:00:00Z", "currency": "euro", "fuelSurchargeBps": 9000,
                 "expressMultiplierBps": 100, "minChargeCents": -1,
                 "bands": [{"zone": "uk", "upToKg": 0, "baseCents": -5, "perKgCents": 1}]}
                """;
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(7)));
    }

    @Test
    void moreThanTwoDecimalsOnABandEdgeIsRefused() throws Exception {
        String t = table("2026-07-01T00:00:00Z", 100).replace("\"upToKg\": 100", "\"upToKg\": 100.005");
        mvc.perform(post("/api/rate-tables").contentType(MediaType.APPLICATION_JSON).content(t))
                .andExpect(status().isUnprocessableEntity());
    }
}
