package com.uteexpress.payment;

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

class RefundLedgerMigrationIT {
    @ParameterizedTest @ValueSource(strings = {"fresh", "receipt", "shipping"})
    void freshAndUpgradePreserveHistoryAndEnforceLedger(String baseline) {
        try (var db = new PostgreSQLContainer("postgres:17.6")) {
            db.start();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            var prior = migration(db, baseline.equals("receipt") ? "20261018090000" : baseline.equals("shipping") ? "20261018110000" : null);
            prior.migrate();
            long actor = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES ('refund@test.invalid','refund@test.invalid','refund','refund','test','ACTIVE') RETURNING id", Long.class);
            long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop','refund-shop','Pickup','APPROVED') RETURNING id", Long.class, actor);
            long first = source(jdbc, actor, shop, "one"), second = source(jdbc, actor, shop, "two"), third = source(jdbc, actor, shop, "three");
            var historical = jdbc.queryForList("SELECT * FROM uteexpress.orders ORDER BY id");
            var paymentHistory = jdbc.queryForList("SELECT * FROM uteexpress.payments ORDER BY id");
            var returnHistory = jdbc.queryForList("SELECT * FROM uteexpress.return_requests ORDER BY id");
            var checksums = jdbc.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history WHERE version < '20261022090000' ORDER BY installed_rank");
            var latest = migration(db, null);
            var pending = Arrays.stream(latest.info().pending()).map(m -> m.getVersion().getVersion()).toList();
            assertThat(pending).containsExactlyElementsOf(baseline.equals("receipt") ? List.of("20261018110000", "20261022090000") : baseline.equals("shipping") ? List.of("20261022090000") : List.of());
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(pending.size());
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(latest.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history WHERE version < '20261022090000' AND version <= ? ORDER BY installed_rank", checksums.getLast().get("version"))).isEqualTo(checksums);
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.orders ORDER BY id")).isEqualTo(historical);
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.payments ORDER BY id")).isEqualTo(paymentHistory);
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.return_requests ORDER BY id")).isEqualTo(returnHistory);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.refunds", Long.class)).isZero();
            long p1 = payment(jdbc, first), p2 = payment(jdbc, second), r1 = request(jdbc, first), r2 = request(jdbc, second);
            invalid(() -> insert(jdbc, first, p2, r1, actor, "foreign-payment", "25", "SUCCEEDED"));
            invalid(() -> insert(jdbc, first, p1, r2, actor, "foreign-return", "25", "SUCCEEDED"));
            invalid(() -> insert(jdbc, first, p1, r1, Long.MAX_VALUE, "foreign-actor", "25", "SUCCEEDED"));
            for (String amount : List.of("0", "-1", "25.01", "NaN")) invalid(() -> insert(jdbc, first, p1, r1, actor, "bad-amount", amount, "SUCCEEDED"));
            invalid(() -> insert(jdbc, first, p1, r1, actor, "bad-status", "25", "PENDING"));
            insert(jdbc, first, p1, r1, actor, "key", "25", "SUCCEEDED");
            invalid(() -> insert(jdbc, first, p1, r1, actor, "different-key", "25", "SUCCEEDED"));
            invalid(() -> insert(jdbc, second, p2, r2, actor, "key", "25", "SUCCEEDED"));
            var committed = jdbc.queryForList("SELECT * FROM uteexpress.refunds");
            for (String column : List.of("proof_reference='OTHER'", "canonical_payload=decode('ff','hex')", "processed_by=" + actor, "amount=26", "server_timestamp=CURRENT_TIMESTAMP"))
                invalid(() -> jdbc.update("UPDATE uteexpress.refunds SET " + column));
            invalid(() -> jdbc.update("DELETE FROM uteexpress.refunds"));
            invalid(() -> jdbc.execute("TRUNCATE uteexpress.refunds"));
            invalid(() -> jdbc.execute("TRUNCATE uteexpress.orders CASCADE"));
            invalid(() -> jdbc.update("DELETE FROM uteexpress.payments WHERE id=?", p1));
            invalid(() -> jdbc.update("DELETE FROM uteexpress.return_requests WHERE id=?", r1));
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.refunds")).usingRecursiveComparison().isEqualTo(committed);
            // Keys in an aborted transaction never become committed reservations.
            var ds = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
            var transactionalJdbc = new JdbcTemplate(ds);
            tx.executeWithoutResult(s -> { insert(transactionalJdbc, third, payment(jdbc, third), request(jdbc, third), actor, "rolled-back", "25", "SUCCEEDED"); s.setRollbackOnly(); });
            insert(jdbc, third, payment(jdbc, third), request(jdbc, third), actor, "rolled-back", "25", "SUCCEEDED");
            System.out.println("PAY02 migration " + baseline + ": pending=" + pending + ", preserved checksums/history, no backfill, composite FKs, append-only, rollback key reuse PASS");
        }
    }
    private long source(JdbcTemplate jdbc, long actor, long shop, String code) {
        long order = jdbc.queryForObject("""
                INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,
                    subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot)
                VALUES (?,?,'hash',?,?,'RETURNED','Receiver','0900000000','VN','District','Detail',20,0,5,25,0,0) RETURNING id
                """, Long.class, code, code, actor, shop);
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at) VALUES (?,'COD','PAID',25,?,'2026-10-20T00:00:00Z')", order, code);
        jdbc.update("""
                INSERT INTO uteexpress.return_requests(order_id,requested_by,reason,status,requested_at,created_at,updated_at,
                    decided_by,decision_at,received_by,received_at,receipt_reference)
                VALUES (?,?,'Reason','APPROVED','2026-10-20T00:00:00Z','2026-10-20T00:00:00Z','2026-10-20T00:00:00Z',
                    ?,'2026-10-20T00:00:00Z',?,'2026-10-20T00:00:00Z','RECEIPT')
                """, order, actor, actor, actor);
        return order;
    }
    private long payment(JdbcTemplate jdbc, long order) { return jdbc.queryForObject("SELECT id FROM uteexpress.payments WHERE order_id=?", Long.class, order); }
    private long request(JdbcTemplate jdbc, long order) { return jdbc.queryForObject("SELECT id FROM uteexpress.return_requests WHERE order_id=?", Long.class, order); }
    private void insert(JdbcTemplate jdbc, long order, long payment, long request, long actor, String key, String amount, String status) {
        jdbc.update("""
                INSERT INTO uteexpress.refunds(order_id,payment_id,return_request_id,processed_by,operation,idempotency_key,
                    schema_version,expected_order_version,expected_return_request_version,canonical_payload,payload_sha256,
                    amount,status,proof_reference,server_timestamp,created_at,result_order_version,payment_paid_at,return_received_at)
                VALUES (?,?,?,?,'PAY02_MANUAL_COD_REFUND_V1',?,1,2,2,decode('01','hex'),decode(repeat('00',32),'hex'),?::numeric,?,'PROOF',
                    '2026-10-23T00:00:00Z','2026-10-23T00:00:00Z',3,'2026-10-20T00:00:00Z','2026-10-20T00:00:00Z')
                """, order, payment, request, actor, key, amount, status);
    }
    private static void invalid(Runnable mutation) { assertThatThrownBy(mutation::run).isInstanceOf(DataIntegrityViolationException.class); }
    private Flyway migration(PostgreSQLContainer db, String target) {
        var config = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).schemas("uteexpress").defaultSchema("uteexpress").locations("classpath:db/migration");
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load();
    }
}
