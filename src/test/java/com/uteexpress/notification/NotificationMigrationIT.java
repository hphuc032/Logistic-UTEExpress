package com.uteexpress.notification;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationMigrationIT {
    @Test void upgradesAnExistingOrd04DatabaseWithoutOutOfOrderMigrations() {
        try (var postgres = new PostgreSQLContainer("postgres:17.6")) {
            postgres.start();
            var config = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas("uteexpress").defaultSchema("uteexpress")
                    .locations("classpath:db/migration");
            config.target(MigrationVersion.fromVersion("20261006090000")).load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            assertThat(jdbc.queryForObject("SELECT to_regclass('uteexpress.notifications')::text", String.class))
                    .isNull();
            var latest = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas("uteexpress").defaultSchema("uteexpress")
                    .locations("classpath:db/migration").load();
            assertThat(latest.migrate().migrationsExecuted).isGreaterThanOrEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT to_regclass('uteexpress.notifications')::text", String.class))
                    .isEqualTo("uteexpress.notifications");
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }
}
