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
