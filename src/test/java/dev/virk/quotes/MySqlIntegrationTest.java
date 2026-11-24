package dev.virk.quotes;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base for every test that touches the database. One real MySQL 8 container is started
 * once per JVM and shared; each test starts from the same rows (rate table v1, nothing else).
 */
@SpringBootTest(properties = "quotes.sweep-enabled=false")
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
public abstract class MySqlIntegrationTest {

    protected static final Instant START = Instant.parse("2026-06-01T12:00:00Z");

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;

    @BeforeEach
    void resetData() {
        jdbc.update("delete from quote");
        jdbc.update("delete from rate_band where rate_table_id in (select id from rate_table where version > 1)");
        jdbc.update("delete from rate_table where version > 1");
        clock.set(START);
    }
}
