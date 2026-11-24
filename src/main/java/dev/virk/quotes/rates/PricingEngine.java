package dev.virk.quotes.rates;

import dev.virk.quotes.quote.ServiceLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Pure arithmetic over one rate table, no database. Money is whole cents in a long.
 *
 * price = band.base + ceil(weightKg) * band.perKg
 * then x express multiplier, x (1 + fuel surcharge), then at least the minimum charge.
 * Each percentage step rounds half up to a cent, once, so the order is part of the contract.
 */
@Component
public class PricingEngine {

    private static final long BPS = 10_000L;

    public long price(RateTable table, String zone, BigDecimal weightKg, ServiceLevel level) {
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new NoRateException("weight must be above zero");
        }
        RateBand band = table.getBands().stream()
                .filter(b -> b.getZone().equals(zone))
                .filter(b -> weightKg.compareTo(b.getUpToKg()) <= 0)
                .findFirst()
                .orElseThrow(() -> new NoRateException(
                        "no rate in table v" + table.getVersion() + " for zone " + zone + " at " + weightKg + " kg"));

        long billableKg = weightKg.setScale(0, RoundingMode.CEILING).longValueExact();
        long charge = Math.addExact(band.getBaseCents(), Math.multiplyExact(billableKg, band.getPerKgCents()));

        if (level == ServiceLevel.EXPRESS) {
            charge = applyBps(charge, table.getExpressMultiplierBps());
        }
        charge = applyBps(charge, (int) (BPS + table.getFuelSurchargeBps()));
        return Math.max(charge, table.getMinChargeCents());
    }

    private static long applyBps(long cents, int bps) {
        return Math.addExact(Math.multiplyExact(cents, (long) bps), BPS / 2) / BPS;
    }
}
