package dev.virk.quotes.quote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "quote")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "destination_zone", nullable = false)
    private String destinationZone;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_level", nullable = false)
    private ServiceLevel serviceLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private String currency;

    @Column(name = "rate_table_version", nullable = false)
    private int rateTableVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** Optimistic lock. Two writers holding the same version cannot both commit. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Quote() {}

    public Quote(String customerEmail, String destinationZone, BigDecimal weightKg, ServiceLevel serviceLevel,
                 long priceCents, String currency, int rateTableVersion, Instant createdAt, Instant expiresAt) {
        this.customerEmail = customerEmail;
        this.destinationZone = destinationZone;
        this.weightKg = weightKg;
        this.serviceLevel = serviceLevel;
        this.priceCents = priceCents;
        this.currency = currency;
        this.rateTableVersion = rateTableVersion;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = QuoteStatus.OPEN;
    }

    public boolean isPastDeadline(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Only an open quote inside its window can be accepted. Caller has checked both. */
    public void accept(Instant now) {
        if (status != QuoteStatus.OPEN) {
            throw new IllegalStateException("quote " + id + " is " + status);
        }
        this.status = QuoteStatus.ACCEPTED;
        this.acceptedAt = now;
    }

    public void expire() {
        if (status != QuoteStatus.OPEN) {
            throw new IllegalStateException("quote " + id + " is " + status);
        }
        this.status = QuoteStatus.EXPIRED;
    }

    public Long getId() { return id; }
    public String getCustomerEmail() { return customerEmail; }
    public String getDestinationZone() { return destinationZone; }
    public BigDecimal getWeightKg() { return weightKg; }
    public ServiceLevel getServiceLevel() { return serviceLevel; }
    public QuoteStatus getStatus() { return status; }
    public long getPriceCents() { return priceCents; }
    public String getCurrency() { return currency; }
    public int getRateTableVersion() { return rateTableVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public long getVersion() { return version; }
}
