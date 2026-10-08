package com.uteexpress.shipping;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shipping.service.ShipmentAssignmentService;
import com.uteexpress.shipping.service.ShipmentFulfillmentService;
import com.uteexpress.shipping.dto.ShipmentStatus;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Testcontainers
class ShipmentFulfillmentIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired ShipmentFulfillmentService fulfillment;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void internalFulfillmentRequiresTransactionLiveAssignmentAndRole() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long first = insertUser("ACTIVE", "SHIPPER");
        long second = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assigned = assignments.assign(order, first, 0L);
        assertThatThrownBy(() -> tx().execute(s -> fulfillment.recordPickedUp(order, 0L)))
                .isInstanceOf(AccessDeniedException.class);
        assignments.reassign(order, second, assigned.version());
        authenticate(first, "SHIPPER");
        assertThatThrownBy(() -> tx().execute(s -> fulfillment.recordPickedUp(order, 1L)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        authenticate(second, "SHIPPER");
        assertThatThrownBy(() -> fulfillment.recordPickedUp(order, 1L))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> tx().execute(s -> fulfillment.recordPickedUp(order, 0L)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        var picked = tx().execute(s -> fulfillment.recordPickedUp(order, 1L));
        assertThat(picked.status()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(picked.pickedUpAt()).isNotNull();
        assertThatThrownBy(() -> tx().execute(s -> fulfillment.recordPickedUp(order, 1L)))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> tx().execute(s -> fulfillment.recordShipping(order, 2L)))
                .isInstanceOf(ApplicationException.class); // Order completion is still required.
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order))
                .isEqualTo("CONFIRMED");
    }

    @Test void provisionalShipmentAndAuditRollbackIfOuterCoordinatorFails() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assigned = assignments.assign(order, shipper, 0L);
        authenticate(shipper, "SHIPPER");
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            fulfillment.recordPickedUp(order, 0L);
            throw new IllegalStateException("Simulated Order completion failure");
        })).isInstanceOf(IllegalStateException.class);
        var facts = tx().execute(s -> fulfillment.lockAssignedForTransition(order, 0L, ShipmentStatus.ASSIGNED));
        assertThat(facts.pickedUpAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=?",
                Long.class, assigned.id())).isEqualTo(1);
    }

    @Test void fulfillmentEvidenceMustBePersistedAndMatchesItsState() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        assignments.assign(order, shipper, 0L);
        authenticate(shipper, "SHIPPER");
        tx().executeWithoutResult(s -> fulfillment.recordPickedUp(order, 0L));
        // Test-only stand-in for TD's lifecycle completion, never a production status writer.
        jdbc.update("UPDATE uteexpress.orders SET status='PICKED_UP' WHERE id=?", order);
        var shipping = tx().execute(s -> fulfillment.recordShipping(order, 1L));
        assertThat(shipping.attemptCount()).isEqualTo(1);
        jdbc.update("UPDATE uteexpress.orders SET status='SHIPPING' WHERE id=?", order);
        var delivered = tx().execute(s -> fulfillment.recordDelivered(order, 2L));
        assertThat(delivered.deliveredAt()).isNotNull();
        var evidence = tx().execute(s -> fulfillment.requireFulfillmentEvidence(order, 3L, ShipmentStatus.DELIVERED));
        assertThat(evidence).isEqualTo(delivered);
        jdbc.update("UPDATE uteexpress.shipments SET delivered_at=NULL WHERE order_id=?", order);
        assertThatThrownBy(() -> tx().execute(s ->
                fulfillment.requireFulfillmentEvidence(order, 3L, ShipmentStatus.DELIVERED)))
                .isInstanceOf(ApplicationException.class);
    }

    @Test void pickupAndVendorCancellationSerializeOnOrderLock() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        long vendor = jdbc.queryForObject("SELECT s.owner_id FROM uteexpress.orders o JOIN uteexpress.shops s ON s.id=o.shop_id WHERE o.id=?",
                Long.class, order);
        authenticate(admin, "ADMIN");
        assignments.assign(order, shipper, 0L);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var pickup = pool.submit(() -> race(start, shipper, "SHIPPER", () -> fulfillment.recordPickedUp(order, 0L)));
            var cancel = pool.submit(() -> race(start, vendor, "VENDOR", () -> fulfillment.cancelAssignedForVendorOrder(order)));
            start.countDown();
            assertThat(List.of(pickup.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    cancel.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?", String.class, order))
                    .isIn("PICKED_UP", "CANCELLED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?)",
                    Long.class, order)).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }

    @Test void vendorClosureChecksOwnerAndRollsBackWithOuterCancellation() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long stranger = insertUser("ACTIVE", "VENDOR");
        long order = order(true, "CONFIRMED", true);
        long vendor = jdbc.queryForObject("SELECT s.owner_id FROM uteexpress.orders o JOIN uteexpress.shops s ON s.id=o.shop_id WHERE o.id=?",
                Long.class, order);
        authenticate(admin, "ADMIN");
        var assigned = assignments.assign(order, shipper, 0L);
        authenticate(stranger, "VENDOR");
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> fulfillment.cancelAssignedForVendorOrder(order)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        authenticate(vendor, "VENDOR");
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            fulfillment.cancelAssignedForVendorOrder(order);
            throw new IllegalStateException("Simulated inventory restore failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE id=?", String.class, assigned.id()))
                .isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=?",
                Long.class, assigned.id())).isEqualTo(1);
        tx().executeWithoutResult(s -> fulfillment.cancelAssignedForVendorOrder(order));
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE id=?", String.class, assigned.id()))
                .isEqualTo("CANCELLED");
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> fulfillment.cancelAssignedForVendorOrder(order)))
                .isInstanceOf(ApplicationException.class);
    }

    private boolean race(java.util.concurrent.CountDownLatch start, long actor, String role, Runnable operation) {
        try {
            start.await();
            authenticate(actor, role);
            tx().executeWithoutResult(s -> operation.run());
            return true;
        } catch (ApplicationException ex) {
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } finally { SecurityContextHolder.clearContext(); }
    }

    private org.springframework.transaction.support.TransactionTemplate tx() {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    private long insertUser(String status, String role) {
        String name = "ship" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email, normalized_email, username, normalized_username,
                    password_hash, status) VALUES (?, ?, ?, ?, 'not-a-password', ?) RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name, status);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id, role_id) SELECT ?, id FROM uteexpress.roles WHERE code=?",
                id, role);
        return id;
    }

    private long order(boolean selection, String status, boolean ready) {
        long buyer = insertUser("ACTIVE", "USER");
        long vendor = insertUser("ACTIVE", "VENDOR");
        long shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops(owner_id, name, slug, pickup_address, status)
                VALUES (?, 'Ship shop', ?, 'Pickup', 'APPROVED') RETURNING id
                """, Long.class, vendor, "ship-" + java.util.UUID.randomUUID());
        long provider = jdbc.queryForObject("""
                INSERT INTO uteexpress.shipping_providers(code, name, active)
                VALUES (?, 'Shipment test provider', true) RETURNING id
                """, Long.class, "SHIP_" + java.util.UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 20).toUpperCase());
        String code = "SHIP-" + java.util.UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.orders(order_code, checkout_key, request_hash, buyer_id, shop_id,
                    status, receiver_name, phone, province_code, district, detail, subtotal,
                    discount_total, shipping_fee, shipping_provider_id, shipping_service_code,
                    grand_total, commission_amount, commission_rate_snapshot, ready_at)
                VALUES (?, ?, 'hash', ?, ?, ?, 'Receiver', '0123456789', 'HCM', 'District',
                    'Street', 100000, 0, 15000, ?, ?, 115000, 0, 0, ?)
                RETURNING id
                """, Long.class, code, code, buyer, shop, status,
                selection ? provider : null, selection ? "STANDARD" : null,
                ready ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    }

    private long providerFor(long order) {
        return jdbc.queryForObject("SELECT shipping_provider_id FROM uteexpress.orders WHERE id=?",
                Long.class, order);
    }

    private void authenticate(long id, String role) {
        var principal = principal(id, role);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private UteExpressPrincipal principal(long id, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new UteExpressPrincipal(id, "ship", null, 0, authorities, true);
    }
}

