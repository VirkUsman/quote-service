package dev.virk.quotes.rates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "rate_band")
public class RateBand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String zone;

    @Column(name = "up_to_kg", nullable = false)
    private BigDecimal upToKg;

    @Column(name = "base_cents", nullable = false)
    private long baseCents;

    @Column(name = "per_kg_cents", nullable = false)
    private long perKgCents;

    protected RateBand() {}

    public RateBand(String zone, BigDecimal upToKg, long baseCents, long perKgCents) {
        this.zone = zone;
        this.upToKg = upToKg;
        this.baseCents = baseCents;
        this.perKgCents = perKgCents;
    }

    public String getZone() { return zone; }
    public BigDecimal getUpToKg() { return upToKg; }
    public long getBaseCents() { return baseCents; }
    public long getPerKgCents() { return perKgCents; }
}
