package dev.virk.quotes.rates;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** An immutable, numbered set of prices. A new set is a new row, never an edit. */
@Entity
@Table(name = "rate_table")
public class RateTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int version;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(nullable = false)
    private String currency;

    @Column(name = "fuel_surcharge_bps", nullable = false)
    private int fuelSurchargeBps;

    @Column(name = "express_multiplier_bps", nullable = false)
    private int expressMultiplierBps;

    @Column(name = "min_charge_cents", nullable = false)
    private long minChargeCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "rate_table_id", nullable = false)
    @OrderBy("zone ASC, upToKg ASC")
    private List<RateBand> bands = new ArrayList<>();

    protected RateTable() {}

    public RateTable(int version, Instant effectiveFrom, String currency, int fuelSurchargeBps,
                     int expressMultiplierBps, long minChargeCents, Instant createdAt, List<RateBand> bands) {
        this.version = version;
        this.effectiveFrom = effectiveFrom;
        this.currency = currency;
        this.fuelSurchargeBps = fuelSurchargeBps;
        this.expressMultiplierBps = expressMultiplierBps;
        this.minChargeCents = minChargeCents;
        this.createdAt = createdAt;
        this.bands = new ArrayList<>(bands);
    }

    public Long getId() { return id; }
    public int getVersion() { return version; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public String getCurrency() { return currency; }
    public int getFuelSurchargeBps() { return fuelSurchargeBps; }
    public int getExpressMultiplierBps() { return expressMultiplierBps; }
    public long getMinChargeCents() { return minChargeCents; }
    public Instant getCreatedAt() { return createdAt; }
    public List<RateBand> getBands() { return List.copyOf(bands); }
}
