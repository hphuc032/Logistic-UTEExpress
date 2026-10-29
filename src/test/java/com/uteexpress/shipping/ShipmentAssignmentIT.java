package com.uteexpress.shipping;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shipping.service.ShipmentAssignmentService;
import com.uteexpress.shipping.service.ShipperAssignmentReadService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ShipmentAssignmentIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired ShipperAssignmentReadService shipperReads;
    @Autowired MockMvc mvc;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void competingAssignmentsWaitForOrderAndCreateOnlyOneShipment() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long manager = insertUser("ACTIVE", "MANAGER");
        long first = insertUser("ACTIVE", "SHIPPER");
        long second = insertUser("ACTIVE", "SHIPPER");
        long id = order(true, "CONFIRMED", true);
        String before = nonAssignmentFacts(id);
        var outcomes = competeBehindOrderLock(id,
                () -> assignmentOutcome(admin, "ADMIN", () -> assignments.assign(id, first, 0L)),
                () -> assignmentOutcome(manager, "MANAGER", () -> assignments.assign(id, second, 0L)));
        assertThat(outcomes).filteredOn(value -> value instanceof com.uteexpress.shipping.dto.ShipmentAssignment).hasSize(1);
        assertThat(outcomes).filteredOn(value -> value == ErrorCode.CONFLICT).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipments WHERE order_id=?", Long.class, id)).isEqualTo(1);
        long shipment = jdbc.queryForObject("SELECT id FROM uteexpress.shipments WHERE order_id=?", Long.class, id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipment_assignment_history WHERE shipment_id=?", Long.class, shipment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=?", Long.class, shipment)).isEqualTo(1);
        assertThat(nonAssignmentFacts(id)).isEqualTo(before);
    }

    @Test void competingReassignmentsUseOriginalVersionAndCommitOnlyOneChange() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long manager = insertUser("ACTIVE", "MANAGER");
        long original = insertUser("ACTIVE", "SHIPPER");
        long first = insertUser("ACTIVE", "SHIPPER");
        long second = insertUser("ACTIVE", "SHIPPER");
        long id = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assigned = assignments.assign(id, original, 0L);
        String before = nonAssignmentFacts(id);
        var outcomes = competeBehindOrderLock(id,
                () -> assignmentOutcome(admin, "ADMIN", () -> assignments.reassign(id, first, assigned.version())),
                () -> assignmentOutcome(manager, "MANAGER", () -> assignments.reassign(id, second, assigned.version())));
        assertThat(outcomes).filteredOn(value -> value instanceof com.uteexpress.shipping.dto.ShipmentAssignment).hasSize(1);
        assertThat(outcomes).filteredOn(value -> value == ErrorCode.CONFLICT).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE id=?", Long.class, assigned.id())).isEqualTo(assigned.version() + 1);
        assertThat(jdbc.queryForObject("SELECT assigned_shipper_id FROM uteexpress.shipments WHERE id=?", Long.class, assigned.id())).isIn(first, second);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipment_assignment_history WHERE shipment_id=?", Long.class, assigned.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=?", Long.class, assigned.id())).isEqualTo(2);
        assertThat(nonAssignmentFacts(id)).isEqualTo(before);
    }

    private Object assignmentOutcome(long actor, String role, java.util.concurrent.Callable<?> operation) throws Exception {
        authenticate(actor, role);
        try { return operation.call(); }
        catch (ApplicationException failure) { return failure.errorCode(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private List<Object> competeBehindOrderLock(long id, java.util.concurrent.Callable<Object> first,
            java.util.concurrent.Callable<Object> second) throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE")) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            var a = pool.submit(first);
            var b = pool.submit(second);
            try {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                int waiting = 0;
                while (System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%uteexpress.orders%'", Integer.class);
                    if (waiting >= 2) break;
                    Thread.sleep(10);
                }
                assertThat(waiting).as("both independent requests wait on the actual PostgreSQL Order lock").isGreaterThanOrEqualTo(2);
            } finally { connection.rollback(); }
            return List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private String nonAssignmentFacts(long id) {
        return jdbc.queryForObject("""
                SELECT json_build_object(
                    'order',(SELECT row_to_json(o) FROM uteexpress.orders o WHERE id=?),
                    'payments',(SELECT coalesce(json_agg(p ORDER BY p.id),'[]'::json) FROM uteexpress.payments p),
                    'refunds',(SELECT coalesce(json_agg(r ORDER BY r.id),'[]'::json) FROM uteexpress.refunds r),
                    'products',(SELECT coalesce(json_agg(p ORDER BY p.id),'[]'::json) FROM uteexpress.products p),
                    'vouchers',(SELECT coalesce(json_agg(v ORDER BY v.id),'[]'::json) FROM uteexpress.voucher_usages v),
                    'order_history',(SELECT coalesce(json_agg(h ORDER BY h.id),'[]'::json) FROM uteexpress.order_status_history h),
                    'notifications',(SELECT coalesce(json_agg(n ORDER BY n.id),'[]'::json) FROM uteexpress.notifications n)
                )::text
                """, String.class, id);
    }

    @Test void assignmentUsesPersistedOrderSelectionAndReassignmentAuditsWithoutChangingOrder() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long first = insertUser("ACTIVE", "SHIPPER");
        long second = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");

        var assigned = assignments.assign(order, first, 0L);
        assertThat(assigned.providerId()).isEqualTo(providerFor(order));
        assertThat(assigned.serviceCode()).isEqualTo("STANDARD");
        assertThat(assigned.fee()).isEqualByComparingTo("15000");
        assertThat(assigned.status().name()).isEqualTo("ASSIGNED");
        assertThat(assigned.shipperId()).isEqualTo(first);
        assertThatThrownBy(() -> assignments.assign(order, second, 0L))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        var changed = assignments.reassign(order, second, assigned.version());
        assertThat(changed.shipperId()).isEqualTo(second);
        assertThat(changed.version()).isEqualTo(assigned.version() + 1);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=?",
                Long.class, assigned.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipment_assignment_history WHERE shipment_id=?",
                Long.class, assigned.id())).isEqualTo(2);
    }

    @Test void rejectsLegacySelectionUnreadyOrderAndUnqualifiedShipper() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long ordinary = insertUser("ACTIVE", "USER");
        authenticate(admin, "ADMIN");
        long legacy = order(false, "CONFIRMED", true);
        assertThatThrownBy(() -> assignments.assign(legacy, shipper, 0L))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        long unready = order(true, "CONFIRMED", false);
        assertThatThrownBy(() -> assignments.assign(unready, shipper, 0L))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        long valid = order(true, "CONFIRMED", true);
        assertThatThrownBy(() -> assignments.assign(valid, ordinary, 0L))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipments WHERE order_id=?",
                Long.class, valid)).isZero();
    }

    @Test void nonOpsRoleCannotAssignOrReassign() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long buyer = insertUser("ACTIVE", "USER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assigned = assignments.assign(order, shipper, 0L);
        authenticate(buyer, "USER");
        assertThatThrownBy(() -> assignments.assign(order, shipper, 0L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assignments.reassign(order, shipper, assigned.version()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void staleVersionsLockedShipperAndPickupBlockAssignmentChanges() {
        long admin = insertUser("ACTIVE", "ADMIN");
        long first = insertUser("ACTIVE", "SHIPPER");
        long second = insertUser("ACTIVE", "SHIPPER");
        long locked = insertUser("LOCKED", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        assertThatThrownBy(() -> assignments.assign(order, first, 1L))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> assignments.assign(order, locked, 0L))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        var assigned = assignments.assign(order, first, 0L);
        assertThatThrownBy(() -> assignments.reassign(order, second, assigned.version() + 1))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        jdbc.update("UPDATE uteexpress.shipments SET status='PICKED_UP' WHERE id=?", assigned.id());
        assertThatThrownBy(() -> assignments.reassign(order, second, assigned.version()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT assigned_shipper_id FROM uteexpress.shipments WHERE id=?",
                Long.class, assigned.id())).isEqualTo(first);
    }

    @Test void routeRequiresOpsRoleAndCsrf() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long buyer = insertUser("ACTIVE", "USER");
        long shipper = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        String body = "{\"shipperId\":" + shipper + ",\"expectedOrderVersion\":0}";
        mvc.perform(post("/admin/orders/{id}/assign", order)
                        .with(user(principal(admin, "ADMIN")))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/orders/{id}/assign", order)
                        .with(user(principal(buyer, "USER"))).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/orders/{id}/assign", order)
                        .with(user(principal(admin, "ADMIN"))).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    @Test void shipperCanOnlyReadCurrentAssignmentsAfterReassignment() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long first = insertUser("ACTIVE", "SHIPPER");
        long second = insertUser("ACTIVE", "SHIPPER");
        long order = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN");
        var assigned = assignments.assign(order, first, 0L);

        authenticate(first, "SHIPPER");
        assertThat(shipperReads.assigned()).extracting(a -> a.id()).contains(assigned.id());
        assertThat(shipperReads.detail(assigned.id()).orderId()).isEqualTo(order);
        mvc.perform(get("/shipper/shipments/{id}", assigned.id())
                .with(user(principal(first, "SHIPPER"))))
                .andExpect(status().isOk());
        mvc.perform(get("/shipper/shipments/{id}", assigned.id())
                .with(user(principal(admin, "ADMIN"))))
                .andExpect(status().isForbidden());

        authenticate(admin, "ADMIN");
        assignments.reassign(order, second, assigned.version());
        authenticate(first, "SHIPPER");
        assertThat(shipperReads.assigned()).extracting(a -> a.id()).doesNotContain(assigned.id());
        assertThatThrownBy(() -> shipperReads.detail(assigned.id()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get("/shipper/shipments/{id}", assigned.id())
                .with(user(principal(first, "SHIPPER"))))
                .andExpect(status().isNotFound());
        authenticate(second, "SHIPPER");
        assertThat(shipperReads.detail(assigned.id()).shipperId()).isEqualTo(second);
    }

    @Test void opsHtmlOffersActiveAssignmentThenReassignmentWithoutChangingTheJsonAuthority() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        long manager = insertUser("ACTIVE", "MANAGER");
        long first = insertUser("ACTIVE", "SHIPPER");
        long second = insertUser("ACTIVE", "SHIPPER");
        long locked = insertUser("LOCKED", "SHIPPER");
        long id = order(true, "CONFIRMED", true);
        var page = mvc.perform(get("/admin/orders/{id}", id).accept("text/html")
                        .with(user(principal(admin, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("data-shipment-assignment")))
                .andReturn();
        var choices = (java.util.List<?>) page.getModelAndView().getModel().get("assignmentShippers");
        assertThat(choices).extracting("id").contains(first, second).doesNotContain(locked);
        assertThat(page.getModelAndView().getModel().get("assignmentAction")).isEqualTo("/admin/orders/" + id + "/assign");
        authenticate(admin, "ADMIN");
        assignments.assign(id, first, 0L);
        var next = mvc.perform(get("/manager/orders/{id}", id).accept("text/html")
                        .with(user(principal(manager, "MANAGER"))))
                .andExpect(status().isOk()).andReturn();
        assertThat(next.getModelAndView().getModel().get("assignmentAction")).isEqualTo("/manager/orders/" + id + "/reassign");
        assertThat(next.getModelAndView().getModel().get("assignmentVersionField")).isEqualTo("expectedShipmentVersion");
        assertThat((java.util.List<?>) next.getModelAndView().getModel().get("assignmentShippers"))
                .extracting("id").doesNotContain(first, locked).contains(second);
        mvc.perform(get("/admin/orders/{id}", id).accept("text/html")
                .with(user(principal(first, "SHIPPER")))).andExpect(status().isForbidden());
    }

    @Test void opsHtmlDoesNotOfferAssignmentForNewOrUnreadyOrders() throws Exception {
        long admin = insertUser("ACTIVE", "ADMIN");
        for (long id : new long[]{order(true, "NEW", false), order(true, "CONFIRMED", false)}) {
            var page = mvc.perform(get("/admin/orders/{id}", id).accept("text/html")
                    .with(user(principal(admin, "ADMIN")))).andExpect(status().isOk()).andReturn();
            assertThat(page.getModelAndView().getModel()).doesNotContainKey("assignmentAction");
        }
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
