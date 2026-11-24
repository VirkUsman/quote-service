package dev.virk.quotes.quote;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByStatusOrderByIdAsc(QuoteStatus status);

    /**
     * Bulk expiry. It has to bump version itself: a bulk update skips Hibernate's
     * versioning, and without the bump an accept holding the old version could
     * still commit over a row the sweep had just expired.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Quote q
               set q.status = dev.virk.quotes.quote.QuoteStatus.EXPIRED, q.version = q.version + 1
             where q.status = dev.virk.quotes.quote.QuoteStatus.OPEN and q.expiresAt <= :now
            """)
    int expireOpenBefore(@Param("now") Instant now);
}
