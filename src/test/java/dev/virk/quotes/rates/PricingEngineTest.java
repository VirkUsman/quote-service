package dev.virk.quotes.rates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.virk.quotes.quote.ServiceLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingEngineTest {

    private final PricingEngine engine = new PricingEngine();

    private static RateTable table(int fuelBps, int expressBps, long minCharge) {
        return new RateTable(7, Instant.parse("2026-01-01T00:00:00Z"), "EUR", fuelBps, expressBps, minCharge,
                Instant.parse("2026-01-01T00:00:00Z"), List.of(
                        new RateBand("EU-W", bd("10"), 1000, 100),
                        new RateBand("EU-W", bd("50"), 1500, 80),
                        new RateBand("UK", bd("20"), 2000, 150)));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void firstBandPricesBasePlusPerKilo() {
        // 1000 + 4 * 100 = 1400, no surcharge
        assertThat(engine.price(table(0, 15000, 0), "EU-W", bd("4"), ServiceLevel.STANDARD)).isEqualTo(1400);
    }

    @Test
    void weightExactlyOnABandEdgeStaysInThatBand() {
        // 10 kg is still band one: 1000 + 10 * 100
        assertThat(engine.price(table(0, 15000, 0), "EU-W", bd("10"), ServiceLevel.STANDARD)).isEqualTo(2000);
    }

    @Test
    void weightJustOverTheEdgeMovesToTheNextBand() {
        // 10.01 kg bills 11 kg in band two: 1500 + 11 * 80
        assertThat(engine.price(table(0, 15000, 0), "EU-W", bd("10.01"), ServiceLevel.STANDARD)).isEqualTo(2380);
    }

    @Test
    void fractionalWeightBillsWholeKilos() {
        // 2.01 kg bills 3 kg: 1000 + 300
        assertThat(engine.price(table(0, 15000, 0), "EU-W", bd("2.01"), ServiceLevel.STANDARD)).isEqualTo(1300);
    }

    @Test
    void fuelSurchargeIsAddedAsBasisPoints() {
        // 1400 * 1.085 = 1519 exactly, and 4 and 4.0 kg are the same weight
        assertThat(engine.price(table(850, 15000, 0), "EU-W", bd("4"), ServiceLevel.STANDARD)).isEqualTo(1519);
        assertThat(engine.price(table(850, 15000, 0), "EU-W", bd("4.0"), ServiceLevel.STANDARD)).isEqualTo(1519);
    }

    @Test
    void halfACentRoundsUp() {
        // 1300 * 1.0005 = 1300.65 -> 1301; 1300 * 1.0003 = 1300.39 -> 1300
        assertThat(engine.price(table(5, 15000, 0), "EU-W", bd("3"), ServiceLevel.STANDARD)).isEqualTo(1301);
        assertThat(engine.price(table(3, 15000, 0), "EU-W", bd("3"), ServiceLevel.STANDARD)).isEqualTo(1300);
    }

    @Test
    void expressAppliesTheMultiplierBeforeFuel() {
        // 1400 * 1.5 = 2100, then * 1.10 = 2310
        assertThat(engine.price(table(1000, 15000, 0), "EU-W", bd("4"), ServiceLevel.EXPRESS)).isEqualTo(2310);
    }

    @Test
    void standardIgnoresTheExpressMultiplier() {
        assertThat(engine.price(table(0, 99999, 0), "EU-W", bd("4"), ServiceLevel.STANDARD)).isEqualTo(1400);
    }

    @Test
    void minimumChargeWinsOverACheapParcel() {
        assertThat(engine.price(table(0, 15000, 5000), "EU-W", bd("1"), ServiceLevel.STANDARD)).isEqualTo(5000);
    }

    @Test
    void minimumChargeDoesNotRaiseAnExpensiveParcel() {
        assertThat(engine.price(table(0, 15000, 500), "EU-W", bd("4"), ServiceLevel.STANDARD)).isEqualTo(1400);
    }

    @Test
    void zonesAreIndependent() {
        assertThat(engine.price(table(0, 15000, 0), "UK", bd("4"), ServiceLevel.STANDARD)).isEqualTo(2600);
    }

    @Test
    void aWeightAboveTheLastBandHasNoRate() {
        assertThatThrownBy(() -> engine.price(table(0, 15000, 0), "UK", bd("20.01"), ServiceLevel.STANDARD))
                .isInstanceOf(NoRateException.class)
                .hasMessageContaining("UK")
                .hasMessageContaining("v7");
    }

    @Test
    void anUnknownZoneHasNoRate() {
        assertThatThrownBy(() -> engine.price(table(0, 15000, 0), "MARS", bd("1"), ServiceLevel.STANDARD))
                .isInstanceOf(NoRateException.class);
    }

    @Test
    void zeroAndNegativeWeightsAreRefused() {
        assertThatThrownBy(() -> engine.price(table(0, 15000, 0), "EU-W", bd("0"), ServiceLevel.STANDARD))
                .isInstanceOf(NoRateException.class);
        assertThatThrownBy(() -> engine.price(table(0, 15000, 0), "EU-W", bd("-3"), ServiceLevel.STANDARD))
                .isInstanceOf(NoRateException.class);
    }
}
