package dev.virk.quotes.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuoteTest {

    private static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");

    private static Quote open() {
        return new Quote("a@b.co", "EU-W", new BigDecimal("3.00"), ServiceLevel.STANDARD, 1300, "EUR", 1,
                T0, T0.plusSeconds(1800));
    }

    @Test
    void aNewQuoteIsOpen() {
        assertThat(open().getStatus()).isEqualTo(QuoteStatus.OPEN);
        assertThat(open().getAcceptedAt()).isNull();
    }

    @Test
    void acceptingRecordsTheTime() {
        Quote q = open();
        q.accept(T0.plusSeconds(60));
        assertThat(q.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(q.getAcceptedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void anAcceptedQuoteCannotBeAcceptedOrExpired() {
        Quote q = open();
        q.accept(T0);
        assertThatThrownBy(() -> q.accept(T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(q::expire).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anExpiredQuoteCannotBeAccepted() {
        Quote q = open();
        q.expire();
        assertThatThrownBy(() -> q.accept(T0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theDeadlineIsInclusive() {
        Quote q = open();
        assertThat(q.isPastDeadline(T0.plusSeconds(1799))).isFalse();
        assertThat(q.isPastDeadline(T0.plusSeconds(1800))).isTrue();
        assertThat(q.isPastDeadline(T0.plusSeconds(1801))).isTrue();
    }
}
