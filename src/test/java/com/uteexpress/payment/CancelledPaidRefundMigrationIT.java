package com.uteexpress.payment;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class CancelledPaidRefundMigrationIT {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void freshAndPopulatedLedgerUpgradePreserveOldResultsAndEnforceBothOperations(boolean upgrade) {
        try(var db=new PostgreSQLContainer("postgres:17.6")) {
            db.start();var ds=new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword());
            var jdbc=new JdbcTemplate(ds);
            var config=Flyway.configure().dataSource(ds).schemas("uteexpress").defaultSchema("uteexpress").locations("classpath:db/migration");
            if(upgrade)config.target("20261027100000");config.load().migrate();
            long user=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES ('schema@example.test','schema@example.test','schema','schema','test-only','ACTIVE') RETURNING id",Long.class);
            long shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Schema','schema','Synthetic','APPROVED') RETURNING id",Long.class,user);
            long returned=order(jdbc,user,shop,"RETURNED","returned");
            long oldPayment=payment(jdbc,returned,"returned");
            long request=jdbc.queryForObject("""
                    INSERT INTO uteexpress.return_requests(order_id,requested_by,reason,status,requested_at,created_at,updated_at,
                        decided_by,decision_at,received_by,received_at,receipt_reference)
                    VALUES (?,?,'Reason','APPROVED','2026-10-20T00:00:00Z','2026-10-20T00:00:00Z','2026-10-20T00:00:00Z',?,'2026-10-20T00:00:00Z',?,'2026-10-20T00:00:00Z','RECEIPT') RETURNING id
                    """,Long.class,returned,user,user,user);
            jdbc.update("""
                    INSERT INTO uteexpress.refunds(order_id,payment_id,return_request_id,processed_by,operation,idempotency_key,
                        schema_version,expected_order_version,expected_return_request_version,canonical_payload,payload_sha256,
                        amount,status,proof_reference,server_timestamp,created_at,result_order_version,payment_paid_at,return_received_at)
                    VALUES (?,?,?,?,'PAY02_MANUAL_COD_REFUND_V1','old',1,2,2,decode('01','hex'),decode(repeat('00',32),'hex'),
                        25,'SUCCEEDED','OLD-PROOF','2026-10-23T00:00:00Z','2026-10-23T00:00:00Z',3,'2026-10-20T00:00:00Z','2026-10-20T00:00:00Z')
                    """,returned,oldPayment,request,user);
            var oldRow=jdbc.queryForMap("SELECT * FROM uteexpress.refunds WHERE order_id=?",returned);
            var orders=jdbc.queryForList("SELECT * FROM uteexpress.orders ORDER BY id");
            var payments=jdbc.queryForList("SELECT * FROM uteexpress.payments ORDER BY id");
            var requests=jdbc.queryForList("SELECT * FROM uteexpress.return_requests ORDER BY id");
            var checksums=jdbc.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history WHERE version<='20261027100000' ORDER BY installed_rank");
            var latest=Flyway.configure().dataSource(ds).schemas("uteexpress").defaultSchema("uteexpress").locations("classpath:db/migration").load();
            assertThat(Arrays.stream(latest.info().pending()).map(i->i.getVersion().getVersion()).toList())
                    .containsExactlyElementsOf(upgrade?List.of("20261028081110"):List.of());
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(upgrade?1:0);
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();assertThat(latest.migrate().migrationsExecuted).isZero();
            var after=jdbc.queryForMap("SELECT * FROM uteexpress.refunds WHERE order_id=?",returned);
            assertThat(after.get("order_cancelled_at")).isNull();if(upgrade)after.remove("order_cancelled_at");
            assertThat(after).usingRecursiveComparison().isEqualTo(oldRow);
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.orders ORDER BY id")).isEqualTo(orders);
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.payments ORDER BY id")).isEqualTo(payments);
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.return_requests ORDER BY id")).isEqualTo(requests);
            assertThat(jdbc.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history WHERE version<='20261027100000' ORDER BY installed_rank")).isEqualTo(checksums);
            long cancelled=order(jdbc,user,shop,"CANCELLED","cancelled"),paid=payment(jdbc,cancelled,"cancelled");
            invalid(()->cancelled(jdbc,cancelled,oldPayment,user,"foreign","NULL","1","2026-10-21T00:00:00Z"));
            invalid(()->cancelled(jdbc,cancelled,paid,user,"return-version","2","1","2026-10-21T00:00:00Z"));
            invalid(()->cancelled(jdbc,cancelled,paid,user,"version","NULL","2","2026-10-21T00:00:00Z"));
            invalid(()->cancelled(jdbc,cancelled,paid,user,"before-paid","NULL","1","2026-10-19T00:00:00Z"));
            cancelled(jdbc,cancelled,paid,user,"new","NULL","1","2026-10-21T00:00:00Z");
            var committed=jdbc.queryForList("SELECT * FROM uteexpress.refunds ORDER BY id");
            invalid(()->cancelled(jdbc,cancelled,paid,user,"duplicate","NULL","1","2026-10-21T00:00:00Z"));
            invalid(()->jdbc.update("UPDATE uteexpress.refunds SET order_cancelled_at=order_cancelled_at"));
            invalid(()->jdbc.update("DELETE FROM uteexpress.refunds"));invalid(()->jdbc.execute("TRUNCATE uteexpress.refunds"));
            assertThat(jdbc.queryForList("SELECT * FROM uteexpress.refunds ORDER BY id")).usingRecursiveComparison().isEqualTo(committed);
        }
    }
    private static long order(JdbcTemplate j,long user,long shop,String status,String code) {
        return j.queryForObject("""
                INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,
                    receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,
                    commission_amount,commission_rate_snapshot,version,created_at)
                VALUES (?,?,'hash',?,?,?,'Synthetic','0900000000','VN','District','Address',20,0,5,25,0,0,1,'2026-10-19T00:00:00Z') RETURNING id
                """,Long.class,code,code,user,shop,status);
    }
    private static long payment(JdbcTemplate j,long order,String key) {
        return j.queryForObject("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at,created_at) VALUES (?,'COD','PAID',25,?,'2026-10-20T00:00:00Z','2026-10-19T00:00:00Z') RETURNING id",Long.class,order,key);
    }
    private static void cancelled(JdbcTemplate j,long order,long payment,long actor,String key,String returnVersion,String resultVersion,String at) {
        j.update("""
                INSERT INTO uteexpress.refunds(order_id,payment_id,processed_by,operation,idempotency_key,schema_version,
                    expected_order_version,expected_return_request_version,canonical_payload,payload_sha256,amount,status,
                    proof_reference,server_timestamp,created_at,result_order_version,payment_paid_at,order_cancelled_at)
                VALUES (?,?,?,'PAY02_CANCELLED_PAID_REFUND_V1',?,1,1,%s,decode('01','hex'),decode(repeat('00',32),'hex'),25,
                    'SUCCEEDED','NEW-PROOF','2026-10-23T00:00:00Z','2026-10-23T00:00:00Z',%s,'2026-10-20T00:00:00Z',?::timestamptz)
                """.formatted(returnVersion,resultVersion),order,payment,actor,key,at);
    }
    private static void invalid(Runnable action) { assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class); }
}
