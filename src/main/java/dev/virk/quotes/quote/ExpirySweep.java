package dev.virk.quotes.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "quotes.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class ExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(ExpirySweep.class);

    private final QuoteService quotes;

    public ExpirySweep(QuoteService quotes) {
        this.quotes = quotes;
    }

    @Scheduled(fixedDelayString = "${quotes.expiry-sweep-ms:60000}")
    public void run() {
        int n = quotes.expireDue();
        if (n > 0) log.info("expired {} quotes", n);
    }
}
