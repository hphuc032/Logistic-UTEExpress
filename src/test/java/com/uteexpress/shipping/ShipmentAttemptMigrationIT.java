package com.uteexpress.shipping;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class ShipmentAttemptMigrationIT {
    @ParameterizedTest @ValueSource(strings={"fresh","phase1","receipt"})
    void freshAndDeployedUpgradeValidateImmutableEvidenceSchema(String baseline) {
        try(var db=new PostgreSQLContainer("postgres:17.6")) {
            db.start();
            var config=Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())
                    .schemas("uteexpress").defaultSchema("uteexpress").locations("classpath:db/migration");
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()));
            if(!baseline.equals("fresh")) {
                config.target(MigrationVersion.fromVersion(baseline.equals("phase1") ? "20261013090000" : "20261018090000")).load().migrate();
                assertThat(jdbc.queryForObject("SELECT to_regclass('uteexpress.shipment_delivery_failures')::text",String.class)).isNull();
            }
            var latest=Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())
                    .schemas("uteexpress").defaultSchema("uteexpress").locations("classpath:db/migration").load();
            int pending=latest.info().pending().length;
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(pending);
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(jdbc.queryForObject("SELECT to_regclass('uteexpress.shipment_delivery_failures')::text",String.class)).isEqualTo("uteexpress.shipment_delivery_failures");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname='tr_preserve_delivery_failure' AND NOT tgisinternal",Integer.class)).isEqualTo(1);
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }
}
