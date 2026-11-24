package dev.virk.quotes.api;

import dev.virk.quotes.quote.Quote;
import dev.virk.quotes.quote.QuoteStatus;
import dev.virk.quotes.quote.ServiceLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class QuoteDtos {

    private QuoteDtos() {}

    public record CreateQuote(
            @NotBlank @Email @Size(max = 254) String customerEmail,
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{2,16}", message = "must be 2 to 16 upper case letters, digits or dashes") String destinationZone,
            @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01") @DecimalMax("100000.00")
            @Digits(integer = 6, fraction = 2) BigDecimal weightKg,
            ServiceLevel serviceLevel) {}

    public record QuoteView(Long id, String customerEmail, String destinationZone, BigDecimal weightKg,
                            ServiceLevel serviceLevel, QuoteStatus status, long priceCents, String currency,
                            int rateTableVersion, Instant createdAt, Instant expiresAt, Instant acceptedAt) {

        public static QuoteView of(Quote q) {
            return new QuoteView(q.getId(), q.getCustomerEmail(), q.getDestinationZone(), q.getWeightKg(),
                    q.getServiceLevel(), q.getStatus(), q.getPriceCents(), q.getCurrency(),
                    q.getRateTableVersion(), q.getCreatedAt(), q.getExpiresAt(), q.getAcceptedAt());
        }
    }

    public record SweepResult(int expired) {}
}
