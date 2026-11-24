package dev.virk.quotes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.virk.quotes.quote.Quote;
import dev.virk.quotes.quote.QuoteExceptions.QuoteAlreadyAcceptedException;
import dev.virk.quotes.quote.QuoteExceptions.QuoteExpiredException;
import dev.virk.quotes.quote.QuoteService;
import dev.virk.quotes.quote.QuoteStatus;
import dev.virk.quotes.quote.ServiceLevel;
import dev.virk.quotes.rates.RateTable;
import dev.virk.quotes.rates.RateTableService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Real threads against a real MySQL. A mock cannot lose a race. */
class ConcurrencyIT extends MySqlIntegrationTest {

    @Autowired QuoteService quotes;
    @Autowired RateTableService tables;

    private Quote newQuote() {
        return quotes.create(new QuoteService.NewQuote("race@example.com", "EU-W", new BigDecimal("3"), ServiceLevel.STANDARD));
    }

    /** Runs every task at the same moment and returns what each one ended in. */
    private static <T> List<Object> fire(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<T> t : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return (Object) t.call();
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Object> out = new ArrayList<>();
            for (Future<Object> f : futures) out.add(f.get());
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Map<String, Long> tally(List<Object> results) {
        Map<String, Long> m = new HashMap<>();
        for (Object r : results) m.merge(r instanceof Throwable t ? t.getClass().getSimpleName() : r.getClass().getSimpleName(), 1L, Long::sum);
        return m;
    }

    @Test
    void sixteenThreadsAcceptingOneQuoteProduceExactlyOneWinner() throws Exception {
        Quote q = newQuote();
        List<Callable<Quote>> tasks = IntStream.range(0, 16).<Callable<Quote>>mapToObj(i -> () -> quotes.accept(q.getId())).toList();
        Map<String, Long> t = tally(fire(tasks));

        assertThat(t.get("Quote")).isEqualTo(1L);
        assertThat(t.get("QuoteAlreadyAcceptedException")).isEqualTo(15L);
        assertThat(quotes.get(q.getId()).getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void theLosersAreToldAlreadyAcceptedNotAServerError() throws Exception {
        Quote q = newQuote();
        List<Callable<Quote>> tasks = IntStream.range(0, 12).<Callable<Quote>>mapToObj(i -> () -> quotes.accept(q.getId())).toList();
        List<Object> results = fire(tasks);
        assertThat(results).filteredOn(r -> r instanceof Throwable)
                .allSatisfy(e -> assertThat(e).isInstanceOf(QuoteAlreadyAcceptedException.class));
    }

    @Test
    void anAcceptedQuoteIsWrittenOnceSoItsVersionMovesOnce() throws Exception {
        Quote q = newQuote();
        long before = quotes.get(q.getId()).getVersion();
        fire(IntStream.range(0, 10).<Callable<Quote>>mapToObj(i -> () -> quotes.accept(q.getId())).toList());
        assertThat(quotes.get(q.getId()).getVersion()).isEqualTo(before + 1);
    }

    @Test
    void twentyQuotesRacedAtOnceEachGetOneWinner() throws Exception {
        List<Quote> made = IntStream.range(0, 20).mapToObj(i -> newQuote()).toList();
        List<Callable<Quote>> tasks = new ArrayList<>();
        for (Quote q : made) for (int i = 0; i < 4; i++) tasks.add(() -> quotes.accept(q.getId()));
        List<Object> results = fire(tasks);

        assertThat(tally(results).get("Quote")).isEqualTo(20L);
        assertThat(jdbc.queryForObject("select count(*) from quote where status = 'ACCEPTED'", Integer.class)).isEqualTo(20);
        assertThat(results.stream().filter(r -> r instanceof Quote).map(r -> ((Quote) r).getId()).distinct().count()).isEqualTo(20);
    }

    @Test
    void acceptRacingTheSweepInsideTheWindowStillHasOneWinner() throws Exception {
        Quote q = newQuote();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> quotes.accept(q.getId()));
            tasks.add(() -> quotes.expireDue());
        }
        List<Object> results = fire(tasks);

        assertThat(results).filteredOn(r -> r instanceof Quote).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof Integer i && i > 0).isEmpty();
        assertThat(quotes.get(q.getId()).getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void acceptRacingTheSweepAfterTheDeadlineNeverAccepts() throws Exception {
        Quote q = newQuote();
        clock.advance(Duration.ofMinutes(45));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> quotes.accept(q.getId()));
            tasks.add(() -> quotes.expireDue());
        }
        List<Object> results = fire(tasks);

        assertThat(results).filteredOn(r -> r instanceof Quote).isEmpty();
        assertThat(results).filteredOn(r -> r instanceof Throwable)
                .allSatisfy(e -> assertThat(e).isInstanceOf(QuoteExpiredException.class));
        Quote end = quotes.get(q.getId());
        assertThat(end.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(end.getAcceptedAt()).isNull();
    }

    @Test
    void concurrentRateTablesGetDistinctConsecutiveVersions() throws Exception {
        RateTableService.NewTable spec = new RateTableService.NewTable(Instant.parse("2027-01-01T00:00:00Z"), "EUR", 0, 15000, 0,
                List.of(new RateTableService.BandSpec("EU-W", new BigDecimal("50"), 1000, 100)));
        List<Callable<RateTable>> tasks = IntStream.range(0, 8).<Callable<RateTable>>mapToObj(i -> () -> tables.create(spec)).toList();
        List<Object> results = fire(tasks);

        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(RateTable.class));
        List<Integer> versions = results.stream().map(r -> ((RateTable) r).getVersion()).sorted().toList();
        assertThat(versions).containsExactly(2, 3, 4, 5, 6, 7, 8, 9);
    }
}
