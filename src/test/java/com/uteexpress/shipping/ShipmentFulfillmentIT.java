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
    @Autowired com.uteexpress.shipping.service.ShipmentAttemptService attempts;
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

    @Test void eligibilityLockLivesUntilOuterTransactionEnds() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        assignments.assign(order, shipper, 0L);
        authenticate(shipper, "SHIPPER");
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            tx().executeWithoutResult(s -> {
                fulfillment.lockAssignedForTransition(order, 0L, ShipmentStatus.ASSIGNED);
                // Independent PostgreSQL transaction must not commit a status update
                // while the coordinator holds eligibility, even before mutation.
                var blocked = pool.submit(() -> {
                    try {
                        tx().executeWithoutResult(other -> {
                            jdbc.execute("SET LOCAL lock_timeout='500ms'");
                            jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", shipper);
                        });
                        return false;
                    } catch (org.springframework.dao.DataAccessException expected) { return true; }
                });
                try { assertThat(blocked.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (Exception ex) { throw new IllegalStateException(ex); }
                fulfillment.recordPickedUp(order, 0L);
            });
            jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", shipper);
            assertThatThrownBy(() -> tx().execute(s -> fulfillment.lockAssignedForTransition(
                    order, 1L, ShipmentStatus.PICKED_UP)))
                    .isInstanceOfSatisfying(ApplicationException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        } finally { pool.shutdownNow(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "MANAGER"})
    void opsCancellationIsSystemWideAndPreservesOrderAndPayment(String role) {
        long actor = insertUser("ACTIVE", role);
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(actor, role);
        var assignment = assignments.assign(order, shipper, 0L);
        var beforeOrder = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order);
        var beforePayment = jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?", order);
        tx().executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
            var locked = fulfillment.lockForOpsCancellation(order).orElseThrow();
            assertThat(locked.shipmentId()).isEqualTo(assignment.id());
            fulfillment.cancelLockedForOpsOrder(order, locked.version());
        });
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order)).isEqualTo(beforeOrder);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?", order)).isEqualTo(beforePayment);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?", String.class, order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?", Long.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=? AND action='SHIPMENT_CANCELLED' AND actor_id=? AND reason='UNABLE_TO_FULFILL'", Long.class, assignment.id(), actor)).isEqualTo(1);
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> fulfillment.cancelLockedForOpsOrder(order, 0L)))
                .isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test void opsHooksRequireTransactionAndOpsRoleAndExplicitAbsence() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long order = order(true, "NEW", false);
        authenticate(admin, "ADMIN");
        assertThatThrownBy(() -> fulfillment.lockForOpsCancellation(order))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> fulfillment.cancelLockedForOpsOrder(order, null))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        tx().executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
            assertThat(fulfillment.lockForOpsCancellation(order)).isEmpty();
            fulfillment.cancelLockedForOpsOrder(order, null);
        });
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> fulfillment.cancelLockedForOpsOrder(order, 0L)))
                .isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        for (String role : List.of("USER", "VENDOR", "SHIPPER")) {
            authenticate(admin, role);
            assertThatThrownBy(() -> tx().execute(s -> fulfillment.lockForOpsCancellation(order))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> tx().executeWithoutResult(s -> fulfillment.cancelLockedForOpsOrder(order, null))).isInstanceOf(AccessDeniedException.class);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"version", "absent", "pickup", "delivered", "attempt", "status"})
    void opsCancellationRejectsStaleOrPhysicalEvidenceWithoutEffects(String kind) {
        long admin = insertUser("ACTIVE", "ADMIN");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assignment = assignments.assign(order, insertUser("ACTIVE", "SHIPPER"), 0L);
        switch (kind) {
            case "pickup" -> jdbc.update("UPDATE uteexpress.shipments SET picked_up_at=CURRENT_TIMESTAMP WHERE order_id=?", order);
            case "delivered" -> jdbc.update("UPDATE uteexpress.shipments SET picked_up_at=CURRENT_TIMESTAMP,delivered_at=CURRENT_TIMESTAMP WHERE order_id=?", order);
            case "attempt" -> jdbc.update("UPDATE uteexpress.shipments SET attempt_count=1 WHERE order_id=?", order);
            case "status" -> jdbc.update("UPDATE uteexpress.shipments SET status='PICKED_UP',picked_up_at=CURRENT_TIMESTAMP WHERE order_id=?", order);
            default -> { }
        }
        var before = jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE id=?", assignment.id());
        var audits = jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=? ORDER BY id", assignment.id());
        Long expected = kind.equals("absent") ? null : kind.equals("version") ? 1L : 0L;
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
            fulfillment.lockForOpsCancellation(order);
            fulfillment.cancelLockedForOpsOrder(order, expected);
        })).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE id=?", assignment.id())).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=? ORDER BY id", assignment.id())).isEqualTo(audits);
    }

    @Test void opsShipmentAndAuditRollbackWithOuterFailure() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assignment = assignments.assign(order, insertUser("ACTIVE", "SHIPPER"), 0L);
        var before = jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE id=?", assignment.id());
        var audits = jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=? ORDER BY id", assignment.id());
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
            fulfillment.lockForOpsCancellation(order);
            fulfillment.cancelLockedForOpsOrder(order, 0L);
            throw new IllegalStateException("Order completion failed after Shipping audit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE id=?", assignment.id())).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=? ORDER BY id", assignment.id())).isEqualTo(audits);
    }

    @Test void opsAuditFailureRollsBackShipmentMutation() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assignment = assignments.assign(order, insertUser("ACTIVE", "SHIPPER"), 0L);
        var before = jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE id=?", assignment.id());
        var audits = jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=? ORDER BY id", assignment.id());
        jdbc.execute("ALTER TABLE uteexpress.audit_logs ADD CONSTRAINT ops_hook_audit_failure CHECK (action <> 'SHIPMENT_CANCELLED') NOT VALID");
        try {
            assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
                fulfillment.lockForOpsCancellation(order);
                fulfillment.cancelLockedForOpsOrder(order, 0L);
            })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally { jdbc.execute("ALTER TABLE uteexpress.audit_logs DROP CONSTRAINT ops_hook_audit_failure"); }
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE id=?", assignment.id())).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=? ORDER BY id", assignment.id())).isEqualTo(audits);
    }

    @Test void opsAbsenceUsesCallerOrderLockAndReadDoesNotLockAccount() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long order = order(true, "CONFIRMED", true);
        long shipper = insertUser("ACTIVE", "SHIPPER");
        authenticate(admin, "ADMIN");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var assignmentPid = new java.util.concurrent.CompletableFuture<Integer>();
        var assigned = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        try {
            tx().executeWithoutResult(s -> {
                int holder = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
                assertThat(fulfillment.lockForOpsCancellation(order)).isEmpty();
                // Another transaction can lock Ops Account while the read hook remains open.
                var account = pool.submit(() -> tx().execute(other -> {
                    jdbc.execute("SET LOCAL lock_timeout='2s'");
                    return jdbc.queryForObject("SELECT id FROM uteexpress.users WHERE id=? FOR UPDATE", Long.class, admin);
                }));
                try { assertThat(account.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(admin); }
                catch (Exception ex) { throw new AssertionError(ex); }
                assigned.add(pool.submit(() -> {
                    authenticate(admin, "ADMIN");
                    try { tx().executeWithoutResult(other -> {
                        jdbc.execute("SET LOCAL lock_timeout='15s'");
                        assignmentPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        assignments.assign(order, shipper, 0L);
                    }); } finally { SecurityContextHolder.clearContext(); }
                }));
                try {
                    int waiter = assignmentPid.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                    boolean blocked = false;
                    while (System.nanoTime() < deadline) {
                        blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter));
                        if (blocked) break;
                        java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10));
                    }
                    assertThat(blocked).as("Assignment waits for caller's Order lock").isTrue();
                    assertThat(fulfillment.lockForOpsCancellation(order)).isEmpty();
                    fulfillment.cancelLockedForOpsOrder(order, null);
                } catch (Exception ex) { throw new AssertionError(ex); }
            });
            assigned.getFirst().get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?", String.class, order)).isEqualTo("ASSIGNED");
        } finally { pool.shutdownNow(); }
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

    @Test void failedDeliveryRetryAndExhaustionPreserveOrderAndImmutableEvidence() {
        long order = shippingAttemptFixture();
        var before = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order);
        tx().executeWithoutResult(s -> {
            attempts.reportFailure(order, 2L, "  No answer  ", "  POD-1  ");
            var evidence = attempts.requireFailureEvidence(order, 2L);
            assertThat(evidence.shipment().version()).isEqualTo(3);
            assertThat(evidence.shipment().attemptCount()).isEqualTo(1);
            assertThat(evidence.failure().failureReason()).isEqualTo("No answer");
            assertThat(evidence.failure().evidenceReference()).isEqualTo("POD-1");
        });
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> attempts.reportFailure(order, 2L, "Again", "POD-1")))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        tx().executeWithoutResult(s -> {
            attempts.retryDelivery(order, 3L);
            var facts = attempts.requireRetryEvidence(order, 3L);
            assertThat(facts.shipment().version()).isEqualTo(4);
            assertThat(facts.shipment().attemptCount()).isEqualTo(2);
            assertThat(facts.failure().attemptNumber()).isEqualTo(1);
            attempts.reportFailure(order, 4L, "Still unavailable", "POD-2");
            assertThat(attempts.requireFailureEvidence(order, 4L).shipment().version()).isEqualTo(5);
        });
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> attempts.retryDelivery(order, 5L)))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        long shipment = jdbc.queryForObject("SELECT id FROM uteexpress.shipments WHERE order_id=?", Long.class,order);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipment_delivery_failures WHERE shipment_id=?",Long.class,shipment)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.shipment_delivery_failures SET evidence_reference='overwrite' WHERE shipment_id=?",shipment))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM uteexpress.shipment_delivery_failures WHERE shipment_id=?",shipment))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order)).isEqualTo(before);
    }

    @Test void failedAuditRollsBackShipmentAndNewFailureEvidence() {
        long order = shippingAttemptFixture();
        var before = jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE order_id=?",order);
        long shipment = ((Number) before.get("id")).longValue();
        jdbc.execute("ALTER TABLE uteexpress.audit_logs ADD CONSTRAINT ship03_audit_fail CHECK (target_id <> "
                + shipment + " OR action <> 'DELIVERY_FAILURE_REPORTED') NOT VALID");
        try {
            assertThatThrownBy(() -> tx().executeWithoutResult(s -> attempts.reportFailure(order,2L,"No answer","PROOF")))
                    .isInstanceOf(RuntimeException.class);
        } finally { jdbc.execute("ALTER TABLE uteexpress.audit_logs DROP CONSTRAINT ship03_audit_fail"); }
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE order_id=?",order)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipment_delivery_failures WHERE shipment_id=?",Long.class,shipment)).isZero();
        tx().executeWithoutResult(s -> attempts.reportFailure(order,2L,"No answer","PROOF"));
    }

    @Test void deliveryAttemptHooksRequireTransactionLiveAssignmentAndBoundedInput() {
        long order = shippingAttemptFixture();
        long shipper = jdbc.queryForObject("SELECT assigned_shipper_id FROM uteexpress.shipments WHERE order_id=?",Long.class,order);
        assertThatThrownBy(() -> attempts.reportFailure(order,2L,"Reason","PROOF"))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        for (String reason : List.of(" ", "x".repeat(1001)))
            assertThatThrownBy(() -> tx().executeWithoutResult(s -> attempts.reportFailure(order,2L,reason,"PROOF")))
                    .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> attempts.reportFailure(order,2L,"Reason","x".repeat(256))))
                .isInstanceOf(ApplicationException.class);
        authenticate(insertUser("ACTIVE","SHIPPER"),"SHIPPER");
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> attempts.reportFailure(order,2L,"Reason","PROOF")))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        authenticate(shipper,"SHIPPER"); jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",shipper);
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> attempts.reportFailure(order,2L,"Reason","PROOF")))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test @org.junit.jupiter.api.Timeout(40)
    void concurrentFailureRequestsCommitExactlyOneEvidence() throws Exception {
        long order = shippingAttemptFixture();
        long shipper = jdbc.queryForObject("SELECT assigned_shipper_id FROM uteexpress.shipments WHERE order_id=?",Long.class,order);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> call = () -> {
            authenticate(shipper,"SHIPPER");
            try {
                assertThat(start.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                tx().executeWithoutResult(s -> {
                    jdbc.execute("SET LOCAL lock_timeout='10s'");
                    attempts.reportFailure(order,2L,"No answer","PROOF");
                }); return true;
            } catch(ApplicationException ex) { assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFLICT); return false; }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first=pool.submit(call); var second=pool.submit(call); start.countDown();
            assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipment_delivery_failures WHERE shipment_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?)",Long.class,order)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?",Long.class,order)).isEqualTo(3);
        } finally { pool.shutdownNow(); }
    }

    private long shippingAttemptFixture() {
        long admin=insertUser("ACTIVE","ADMIN"), shipper=insertUser("ACTIVE","SHIPPER");
        long order=order(true,"CONFIRMED",true);
        authenticate(admin,"ADMIN"); assignments.assign(order,shipper,0L); authenticate(shipper,"SHIPPER");
        tx().executeWithoutResult(s -> fulfillment.recordPickedUp(order,0L));
        // Test fixture only: Order boundary is TD-owned and not on remote yet.
        jdbc.update("UPDATE uteexpress.orders SET status='PICKED_UP' WHERE id=?",order);
        tx().executeWithoutResult(s -> fulfillment.recordShipping(order,1L));
        jdbc.update("UPDATE uteexpress.orders SET status='SHIPPING' WHERE id=?",order);
        return order;
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
