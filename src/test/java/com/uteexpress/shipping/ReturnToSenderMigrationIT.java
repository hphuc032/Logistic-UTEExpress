package com.uteexpress.shipping;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Testcontainers
class ReturnToSenderMigrationIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test void freshApplicationValidatesAndNewMigrationHasNamedParentConstraints() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.flyway_schema_history WHERE version='20261027100000' AND success",Integer.class)).isOne();
        assertThat(jdbc.queryForList("SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema='uteexpress' AND table_name='shipment_return_receipts'",String.class))
                .contains("uq_shipment_return_receipts_order","uq_shipment_return_receipts_shipment","fk_shipment_return_receipts_parent","fk_shipment_return_receipts_actor");
    }

    @Test void populatedReviewBaselineUpgradesWithoutRewritingHistoricalRowsOrChecksums() {
        try (var db=new PostgreSQLContainer("postgres:17.6")) {
            db.start();
            var source=new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword());
            var sql=new JdbcTemplate(source);
            Flyway.configure().dataSource(source).schemas("uteexpress").defaultSchema("uteexpress")
                    .locations("classpath:db/migration").target("20261023170000").load().migrate();
            String key=UUID.randomUUID().toString();
            long user=sql.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES ('migration@example.test','migration@example.test','migration','migration','test-only','ACTIVE') RETURNING id",Long.class);
            long shop=sql.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Migration',?,'Pickup','APPROVED') RETURNING id",Long.class,user,key);
            long provider=sql.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES ('MIGRATION','Migration',true) RETURNING id",Long.class);
            long order=sql.queryForObject("""
                    INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,
                        subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot)
                    VALUES (?,?,'hash',?,?,'SHIPPING','Test','0900000000','VN','District','Test',100,0,0,100,0,0) RETURNING id
                    """,Long.class,key,key,user,shop);
            sql.update("INSERT INTO uteexpress.shipments(order_id,provider_id,assigned_shipper_id,status,shipping_service_snapshot,fee_snapshot,attempt_count,picked_up_at,version) VALUES (?,?,?,'DELIVERY_FAILED','STANDARD',0,2,CURRENT_TIMESTAMP,5)",order,provider,user);
            var beforeOrder=sql.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order);
            var beforeShipment=sql.queryForMap("SELECT * FROM uteexpress.shipments WHERE order_id=?",order);
            var checksums=sql.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history WHERE version IS NOT NULL ORDER BY version");
            var upgrade=Flyway.configure().dataSource(source).schemas("uteexpress").defaultSchema("uteexpress")
                    .locations("classpath:db/migration").load();
            assertThat(java.util.Arrays.stream(upgrade.info().pending()).map(m->m.getVersion().getVersion()).toList())
                    .containsExactly("20261027100000","20261028081110");
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            assertThat(sql.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order)).isEqualTo(beforeOrder);
            assertThat(sql.queryForMap("SELECT * FROM uteexpress.shipments WHERE order_id=?",order)).isEqualTo(beforeShipment);
            assertThat(sql.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history WHERE version IS NOT NULL AND version<='20261023170000' ORDER BY version")).isEqualTo(checksums);
            assertThat(sql.queryForObject("SELECT count(*) FROM uteexpress.shipment_return_receipts",Integer.class)).isZero();
        }
    }
}
