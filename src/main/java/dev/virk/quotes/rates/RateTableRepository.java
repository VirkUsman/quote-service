package dev.virk.quotes.rates;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RateTableRepository extends JpaRepository<RateTable, Long> {

    Optional<RateTable> findByVersion(int version);

    /** The table in force at an instant: latest effective date, highest version on a tie. */
    Optional<RateTable> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(Instant at);

    List<RateTable> findAllByOrderByVersionAsc();

    @Query("select coalesce(max(t.version), 0) from RateTable t")
    int maxVersion();
}
