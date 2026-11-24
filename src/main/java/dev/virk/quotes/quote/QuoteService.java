package dev.virk.quotes.quote;

import dev.virk.quotes.QuoteProperties;
import dev.virk.quotes.quote.QuoteExceptions.QuoteAlreadyAcceptedException;
import dev.virk.quotes.quote.QuoteExceptions.QuoteExpiredException;
import dev.virk.quotes.quote.QuoteExceptions.QuoteNotFoundException;
import dev.virk.quotes.rates.PricingEngine;
import dev.virk.quotes.rates.RateTable;
import dev.virk.quotes.rates.RateTableService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QuoteService {

    public record NewQuote(String customerEmail, String destinationZone, BigDecimal weightKg, ServiceLevel serviceLevel) {}

    private enum Outcome { ACCEPTED, EXPIRED, ALREADY_ACCEPTED }

    private static final int ACCEPT_ATTEMPTS = 3;

    private final QuoteRepository quotes;
    private final RateTableService rates;
    private final PricingEngine engine;
    private final QuoteProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public QuoteService(QuoteRepository quotes, RateTableService rates, PricingEngine engine,
                        QuoteProperties props, Clock clock, TransactionTemplate tx) {
        this.quotes = quotes;
        this.rates = rates;
        this.engine = engine;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
    }

    public Quote create(NewQuote req) {
        Instant now = clock.instant();
        RateTable table = rates.activeAt(now);
        ServiceLevel level = req.serviceLevel() == null ? ServiceLevel.STANDARD : req.serviceLevel();
        long price = engine.price(table, req.destinationZone(), req.weightKg(), level);
        return quotes.save(new Quote(req.customerEmail(), req.destinationZone(), req.weightKg(), level,
                price, table.getCurrency(), table.getVersion(), now, now.plus(props.validity())));
    }

    public Quote get(long id) {
        return quotes.findById(id).orElseThrow(() -> new QuoteNotFoundException(id));
    }

    public List<Quote> list(QuoteStatus status) {
        return status == null ? quotes.findAll() : quotes.findByStatusOrderByIdAsc(status);
    }

    /**
     * Accept exactly once. Two callers can read the same OPEN row; the version column
     * lets only one UPDATE through. The loser re-reads and reports what actually happened
     * to the row (accepted by someone else, or expired by the sweep).
     */
    public Quote accept(long id) {
        for (int attempt = 1; ; attempt++) {
            try {
                Quote[] holder = new Quote[1];
                Outcome outcome = tx.execute(status -> {
                    Quote q = quotes.findById(id).orElseThrow(() -> new QuoteNotFoundException(id));
                    holder[0] = q;
                    Instant now = clock.instant();
                    if (q.getStatus() == QuoteStatus.ACCEPTED) return Outcome.ALREADY_ACCEPTED;
                    if (q.getStatus() == QuoteStatus.EXPIRED) return Outcome.EXPIRED;
                    if (q.isPastDeadline(now)) {
                        q.expire();
                        quotes.saveAndFlush(q);
                        return Outcome.EXPIRED;
                    }
                    q.accept(now);
                    quotes.saveAndFlush(q);
                    return Outcome.ACCEPTED;
                });
                return switch (outcome) {
                    case ACCEPTED -> holder[0];
                    case EXPIRED -> throw new QuoteExpiredException(id);
                    case ALREADY_ACCEPTED -> throw new QuoteAlreadyAcceptedException(id);
                };
            } catch (OptimisticLockingFailureException lost) {
                // Someone changed the row between our read and our write. Look again.
                if (attempt >= ACCEPT_ATTEMPTS) throw lost;
            }
        }
    }

    /** Expire every open quote whose window has closed. Returns how many changed. */
    public int expireDue() {
        Instant now = clock.instant();
        Integer n = tx.execute(status -> quotes.expireOpenBefore(now));
        return n == null ? 0 : n;
    }
}
