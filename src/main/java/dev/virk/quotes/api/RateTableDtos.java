package dev.virk.quotes.api;

import dev.virk.quotes.rates.RateBand;
import dev.virk.quotes.rates.RateTable;
import dev.virk.quotes.rates.RateTableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RateTableDtos {

    private RateTableDtos() {}

    public record BandInput(
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{2,16}") String zone,
            @NotNull @DecimalMin("0.01") BigDecimal upToKg,
            @Min(0) long baseCents,
            @Min(0) long perKgCents) {}

    public record CreateRateTable(
            @NotNull Instant effectiveFrom,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a three letter currency code") String currency,
            @Min(0) @Max(5000) int fuelSurchargeBps,
            @Min(10000) @Max(50000) int expressMultiplierBps,
            @Min(0) long minChargeCents,
            @NotEmpty @Valid List<BandInput> bands) {

        RateTableService.NewTable toNew() {
            return new RateTableService.NewTable(effectiveFrom, currency, fuelSurchargeBps, expressMultiplierBps,
                    minChargeCents, bands.stream()
                    .map(b -> new RateTableService.BandSpec(b.zone(), b.upToKg(), b.baseCents(), b.perKgCents()))
                    .toList());
        }
    }

    public record BandView(String zone, BigDecimal upToKg, long baseCents, long perKgCents) {
        static BandView of(RateBand b) {
            return new BandView(b.getZone(), b.getUpToKg(), b.getBaseCents(), b.getPerKgCents());
        }
    }

    public record RateTableView(int version, Instant effectiveFrom, String currency, int fuelSurchargeBps,
                                int expressMultiplierBps, long minChargeCents, Instant createdAt,
                                List<BandView> bands) {
        public static RateTableView of(RateTable t) {
            return new RateTableView(t.getVersion(), t.getEffectiveFrom(), t.getCurrency(), t.getFuelSurchargeBps(),
                    t.getExpressMultiplierBps(), t.getMinChargeCents(), t.getCreatedAt(),
                    t.getBands().stream().map(BandView::of).toList());
        }
    }
}
