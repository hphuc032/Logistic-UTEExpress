package com.uteexpress.order;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class ReturnReceiptMigrationIT {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void freshAndPhase1UpgradePreserveAllStatusesAndEnforceReceiptContract(boolean upgrade) {
        try (var db = new PostgreSQLContainer("postgres:17.6")) {
            db.start();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            if (upgrade) migration(db, "20261013090000").migrate();
            else migration(db, null).migrate();
            long actor = jdbc.queryForObject("""
                    INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status)
                    VALUES ('receipt@example.test','receipt@example.test','receipt','receipt','test-only','ACTIVE') RETURNING id
                    """, Long.class);
            long shop = jdbc.queryForObject("""
                    INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status)
                    VALUES (?,'Shop','receipt-shop','Pickup','APPROVED') RETURNING id
                    """, Long.class, actor);
            for (String status : List.of("PENDING", "APPROVED", "REJECTED")) {
                long order = jdbc.queryForObject("""
                        INSERT INTO uteexpress.orders
                        (order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,
                         subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot)
                        VALUES (?,?,'hash',?,?,'RETURN_REQUESTED','Receiver','0900000000','VN','District','Detail',20,0,5,25,0,0)
                        RETURNING id
                        """, Long.class, status, status, actor, shop);
                jdbc.update("""
                        INSERT INTO uteexpress.return_requests
                        (order_id,requested_by,reason,status,requested_at,created_at,updated_at,decided_by,decision_at,decision_note)
                        VALUES (?,?,'Reason',?,'2026-10-15T00:00:00Z','2026-10-15T00:00:00Z',
                        '2026-10-15T00:00:00Z',?,?::timestamptz,?)
                        """, order, actor, status, status.equals("PENDING") ? null : actor,
                        status.equals("PENDING") ? null : "2026-10-15T00:00:00Z", status.equals("REJECTED") ? "No" : null);
            }
            var before = jdbc.queryForList("SELECT * FROM uteexpress.return_requests ORDER BY id");
            var phase1Checksum = jdbc.queryForObject("SELECT checksum FROM uteexpress.flyway_schema_history WHERE version='20261013090000'", Integer.class);
            var latest = migration(db, null);
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(upgrade ? 1 : 0);
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(latest.info().pending()).isEmpty();
            assertThat(latest.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT checksum FROM uteexpress.flyway_schema_history WHERE version='20261013090000'", Integer.class))
                    .isEqualTo(phase1Checksum);
            var after = jdbc.queryForList("SELECT * FROM uteexpress.return_requests ORDER BY id");
            if (upgrade) for (var row : after) {
                assertThat(row.remove("received_by")).isNull(); assertThat(row.remove("receipt_reference")).isNull();
            }
            assertThat(after).isEqualTo(before);
            long approved = jdbc.queryForObject("SELECT id FROM uteexpress.return_requests WHERE status='APPROVED'", Long.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.return_requests SET received_at=decision_at WHERE id=?", approved))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE uteexpress.return_requests SET received_at=decision_at,received_by=9223372036854775807,
                    receipt_reference='PROOF' WHERE id=?
                    """, approved)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.update("""
                    UPDATE uteexpress.return_requests SET received_at=decision_at,received_by=?,receipt_reference='PROOF'
                    WHERE id=?
                    """, actor, approved)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.return_requests SET receipt_reference='OVERWRITE' WHERE id=?", approved))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema='uteexpress' AND table_name='return_requests' AND column_name='restockable'", String.class))
                    .isEqualTo("YES");
        }
    }

    private Flyway migration(PostgreSQLContainer db, String target) {
        var config = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas("uteexpress").defaultSchema("uteexpress").locations("classpath:db/migration");
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load();
    }
}
