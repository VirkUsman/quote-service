package dev.virk.quotes.rates;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RateTableService {

    public record BandSpec(String zone, BigDecimal upToKg, long baseCents, long perKgCents) {}

    public record NewTable(Instant effectiveFrom, String currency, int fuelSurchargeBps,
                           int expressMultiplierBps, long minChargeCents, List<BandSpec> bands) {}

    private static final int VERSION_ATTEMPTS = 25;

    private final RateTableRepository tables;
    private final Clock clock;
    private final TransactionTemplate tx;

    public RateTableService(RateTableRepository tables, Clock clock, TransactionTemplate tx) {
        this.tables = tables;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * Version is max + 1, read and inserted in one transaction. Two callers can read the same
     * max; the unique index on version lets one insert through and the other retries with a
     * fresh read. The index is what guarantees uniqueness, the retry just makes the loser succeed.
     */
    public RateTable create(NewTable spec) {
        checkBands(spec.bands());
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> insertNext(spec));
            } catch (DataIntegrityViolationException clash) {
                if (attempt >= VERSION_ATTEMPTS) throw clash;
                Thread.onSpinWait();
            }
        }
    }

    private RateTable insertNext(NewTable spec) {
        int next = tables.maxVersion() + 1;
        List<RateBand> bands = spec.bands().stream()
                .map(b -> new RateBand(b.zone(), b.upToKg().setScale(2, java.math.RoundingMode.UNNECESSARY),
                        b.baseCents(), b.perKgCents()))
                .toList();
        return tables.saveAndFlush(new RateTable(next, spec.effectiveFrom(), spec.currency(),
                spec.fuelSurchargeBps(), spec.expressMultiplierBps(), spec.minChargeCents(),
                clock.instant(), bands));
    }

    @Transactional(readOnly = true)
    public RateTable get(int version) {
        return tables.findByVersion(version)
                .orElseThrow(() -> new RateTableNotFoundException("no rate table v" + version));
    }

    @Transactional(readOnly = true)
    public List<RateTable> list() {
        return tables.findAllByOrderByVersionAsc();
    }

    @Transactional(readOnly = true)
    public RateTable activeAt(Instant at) {
        return tables.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(at)
                .orElseThrow(() -> new RateTableNotFoundException("no rate table is in force at " + at));
    }

    /** A zone's bands must be listed with distinct, positive upper bounds. */
    private static void checkBands(List<BandSpec> bands) {
        if (bands == null || bands.isEmpty()) {
            throw new InvalidRateTableException("a rate table needs at least one band");
        }
        Set<String> seen = new HashSet<>();
        for (BandSpec b : bands) {
            if (b.upToKg().scale() > 2) {
                throw new InvalidRateTableException("upToKg allows two decimals at most: " + b.upToKg());
            }
            if (!seen.add(b.zone() + "|" + b.upToKg().setScale(2))) {
                throw new InvalidRateTableException("zone " + b.zone() + " has two bands ending at " + b.upToKg());
            }
        }
    }
}
