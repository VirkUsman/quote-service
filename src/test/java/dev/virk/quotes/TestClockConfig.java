package dev.virk.quotes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class TestClockConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(MySqlIntegrationTest.START);
    }
}
