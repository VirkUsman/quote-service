package dev.virk.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Runs the migrations against schemas that start empty, on the same MySQL the other tests use.
 * Each test makes its own schema, so nothing here touches the application's data.
 */
class MigrationIT extends MySqlIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private record Schema(String name, JdbcTemplate jdbc, Flyway flyway) {}

    private Schema freshSchema(String target) throws SQLException {
        String name = "mig_" + SEQ.incrementAndGet();
        String base = MYSQL.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/$1");
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute("drop database if exists " + name);
            s.execute("create database " + name);
        }
        String url = base.replaceFirst("/(\\?|$)", "/" + name + "$1");
        Flyway flyway = Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration").target(target).load();
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "root", MYSQL.getPassword());
        return new Schema(name, new JdbcTemplate(ds), flyway);
    }

    @Test
    void anEmptyDatabaseMigratesToTheLatestVersion() throws Exception {
        Schema s = freshSchema("latest");
        MigrateResult r = s.flyway().migrate();
        assertThat(r.success).isTrue();
        assertThat(r.migrationsExecuted).isEqualTo(3);
        assertThat(s.flyway().info().current().getVersion().getVersion()).isEqualTo("3");
    }

    @Test
    void everyMigrationEndsInStateSuccess() throws Exception {
        Schema s = freshSchema("latest");
        s.flyway().migrate();
        assertThat(s.flyway().info().all()).allSatisfy(m -> assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS));
        s.flyway().validate();
    }

    @Test
    void migratingAgainDoesNothing() throws Exception {
        Schema s = freshSchema("latest");
        s.flyway().migrate();
        assertThat(s.flyway().migrate().migrationsExecuted).isZero();
    }

    @Test
    void theSeedMigrationLoadsRateTableOneWithNineBands() throws Exception {
        Schema s = freshSchema("latest");
        s.flyway().migrate();
        assertThat(s.jdbc().queryForObject("select count(*) from rate_table", Integer.class)).isEqualTo(1);
        assertThat(s.jdbc().queryForObject("select count(*) from rate_band", Integer.class)).isEqualTo(9);
        assertThat(s.jdbc().queryForObject("select fuel_surcharge_bps from rate_table where version = 1", Integer.class)).isEqualTo(800);
    }

    @Test
    void aQuoteWrittenBeforeServiceLevelExistedBecomesStandard() throws Exception {
        Schema s = freshSchema("2");
        s.flyway().migrate();
        s.jdbc().update("""
                insert into quote (customer_email, destination_zone, weight_kg, status, price_cents, currency,
                                   rate_table_version, created_at, expires_at, version)
                values ('old@example.com', 'UK', 3.00, 'OPEN', 2620, 'EUR', 1, '2026-02-01 10:00:00', '2026-02-01 10:30:00', 0)
                """);
        assertThat(s.flyway().info().current().getVersion().getVersion()).isEqualTo("2");

        s.flyway().getConfiguration();
        Flyway latest = Flyway.configure().configuration(s.flyway().getConfiguration()).target("latest").load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(s.jdbc().queryForObject("select service_level from quote", String.class)).isEqualTo("STANDARD");
        assertThat(s.jdbc().queryForObject("select price_cents from quote", Long.class)).isEqualTo(2620L);
    }

    @Test
    void twoRateTablesCannotShareAVersion() throws Exception {
        Schema s = freshSchema("latest");
        s.flyway().migrate();
        assertThatThrownBy(() -> s.jdbc().update("""
                insert into rate_table (version, effective_from, currency, fuel_surcharge_bps, express_multiplier_bps, min_charge_cents, created_at)
                values (1, '2026-03-01', 'EUR', 0, 15000, 0, '2026-03-01')
                """)).hasMessageContaining("uq_rate_table_version");
    }

    @Test
    void aQuoteMustPointAtARealRateVersion() throws Exception {
        Schema s = freshSchema("latest");
        s.flyway().migrate();
        assertThatThrownBy(() -> s.jdbc().update("""
                insert into quote (customer_email, destination_zone, weight_kg, service_level, status, price_cents, currency,
                                   rate_table_version, created_at, expires_at, version)
                values ('a@b.co', 'UK', 1.00, 'STANDARD', 'OPEN', 1, 'EUR', 42, now(), now(), 0)
                """)).hasMessageContaining("fk_quote_rate_version");
    }

    @Test
    void theDatabaseRefusesAnAcceptedQuoteWithNoAcceptTime() throws Exception {
        Schema s = freshSchema("latest");
        s.flyway().migrate();
        assertThatThrownBy(() -> s.jdbc().update("""
                insert into quote (customer_email, destination_zone, weight_kg, service_level, status, price_cents, currency,
                                   rate_table_version, created_at, expires_at, version)
                values ('a@b.co', 'UK', 1.00, 'STANDARD', 'ACCEPTED', 1, 'EUR', 1, now(), now(), 0)
                """)).hasMessageContaining("ck_quote_accepted");
    }

    @Test
    void theDatabaseRefusesAnUnknownStatus() throws Exception {
        Schema s = freshSchema("latest");
        s.flyway().migrate();
        assertThatThrownBy(() -> s.jdbc().update("""
                insert into quote (customer_email, destination_zone, weight_kg, service_level, status, price_cents, currency,
                                   rate_table_version, created_at, expires_at, version)
                values ('a@b.co', 'UK', 1.00, 'STANDARD', 'LOST', 1, 'EUR', 1, now(), now(), 0)
                """)).hasMessageContaining("ck_quote_status");
    }
}
