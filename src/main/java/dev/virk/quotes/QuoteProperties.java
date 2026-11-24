package dev.virk.quotes;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * validity: how long a quote can be accepted after it is created.
 * sweepEnabled: the scheduled expiry sweep. Accept does not depend on it,
 * because accept checks the clock itself.
 */
@ConfigurationProperties(prefix = "quotes")
public record QuoteProperties(Duration validity, Long expirySweepMs, Boolean sweepEnabled) {

    public QuoteProperties {
        if (validity == null) validity = Duration.ofMinutes(30);
        if (expirySweepMs == null) expirySweepMs = 60_000L;
        if (sweepEnabled == null) sweepEnabled = true;
    }
}
