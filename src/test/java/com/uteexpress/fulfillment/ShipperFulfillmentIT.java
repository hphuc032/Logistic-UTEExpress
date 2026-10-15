package com.uteexpress.fulfillment;

import com.uteexpress.fulfillment.dto.FulfillmentRequest;
import com.uteexpress.fulfillment.service.ShipperFulfillmentService;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.shipping.service.ShipmentAssignmentService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Testcontainers @AutoConfigureMockMvc
class ShipperFulfillmentIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired ShipperFulfillmentService fulfillment;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.uteexpress.order.service.OrderLifecycleService orderLifecycle;
    long orderId, shipper;
    @BeforeEach void setup() {
        long admin = insertUser("ACTIVE", "ADMIN");
        shipper = insertUser("ACTIVE", "SHIPPER");
        orderId = order(true, "CONFIRMED", true);
        authenticate(admin, "ADMIN"); assignments.assign(orderId, shipper, 0L);
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',115000,?)", orderId, "fulfill-"+orderId);
        authenticate(shipper, "SHIPPER");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void httpFlowSynchronizesShipmentOrderAndCollectsCod() throws Exception {
        String[] routes = {"pickup", "shipping", "delivered"};
        String[] statuses = {"PICKED_UP", "SHIPPING", "DELIVERED"};
        for (int i=0; i<3; i++) {
            String json="{\"expectedOrderVersion\":"+i+",\"expectedShipmentVersion\":"+i+(i==2?",\"collectedAmount\":115000":"")+"}";
            mvc.perform(post("/shipper/orders/"+orderId+"/"+routes[i]).with(user(principal(shipper,"SHIPPER"))).with(csrf())
                            .contentType("application/json").content(json))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(statuses[i]));
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?",String.class,orderId)).isEqualTo(statuses[i]);
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?",String.class,orderId)).isEqualTo(statuses[i]);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?",String.class,orderId)).isEqualTo("PAID");
    }
    @Test void roleCsrfForeignAssignmentAndValidationAreGuarded() throws Exception {
        String path="/shipper/orders/"+orderId+"/pickup";
        String json="{\"expectedOrderVersion\":0,\"expectedShipmentVersion\":0}";
        for(String role:List.of("ADMIN","MANAGER","USER","VENDOR"))
            mvc.perform(post(path).with(user(principal(shipper,role))).with(csrf()).contentType("application/json").content(json)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(principal(shipper,"SHIPPER"))).contentType("application/json").content(json)).andExpect(status().isForbidden());
        long stranger=insertUser("ACTIVE","SHIPPER");
        mvc.perform(post(path).with(user(principal(stranger,"SHIPPER"))).with(csrf()).contentType("application/json").content(json)).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user(principal(shipper,"SHIPPER"))).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
    }
    @Test void wrongCodRollsBackAndDuplicateDeliveryConflicts() {
        fulfillment.transition(orderId,OrderAction.PICK_UP,new FulfillmentRequest(0L,0L,null));
        fulfillment.transition(orderId,OrderAction.START_SHIPPING,new FulfillmentRequest(1L,1L,null));
        var before=state();
        assertThatThrownBy(()->fulfillment.transition(orderId,OrderAction.DELIVER,new FulfillmentRequest(2L,2L,new BigDecimal("1")))).isInstanceOf(ApplicationException.class);
        assertThat(state()).isEqualTo(before);
        fulfillment.transition(orderId,OrderAction.DELIVER,new FulfillmentRequest(2L,2L,new BigDecimal("115000")));
        var delivered=state();
        assertThatThrownBy(()->fulfillment.transition(orderId,OrderAction.DELIVER,new FulfillmentRequest(2L,2L,new BigDecimal("115000"))))
                .isInstanceOfSatisfying(ApplicationException.class,e->assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(state()).isEqualTo(delivered);
    }
    @Test void pageFormUsesVersionsAndCsrf() throws Exception {
        mvc.perform(get("/shipper/shipments/{id}/view", jdbc.queryForObject("SELECT id FROM uteexpress.shipments WHERE order_id=?",Long.class,orderId))
                        .with(user(principal(shipper,"SHIPPER"))).accept("text/html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("expectedOrderVersion")));
        mvc.perform(post("/shipper/orders/{id}/pickup",orderId).with(user(principal(shipper,"SHIPPER")))
                        .contentType("application/x-www-form-urlencoded").param("expectedOrderVersion","0").param("expectedShipmentVersion","0"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/shipper/orders/{id}/pickup",orderId).with(user(principal(shipper,"SHIPPER"))).with(csrf())
                        .contentType("application/x-www-form-urlencoded").param("expectedOrderVersion","0").param("expectedShipmentVersion","0"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shipper/shipments/view"));
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?",String.class,orderId)).isEqualTo("PICKED_UP");
    }
    @Test void competingCoordinatorDeliveriesCommitOnce() throws Exception {
        fulfillment.transition(orderId,OrderAction.PICK_UP,new FulfillmentRequest(0L,0L,null));
        fulfillment.transition(orderId,OrderAction.START_SHIPPING,new FulfillmentRequest(1L,1L,null));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> attempt=()->{
            try {
                authenticate(shipper,"SHIPPER"); start.await();
                fulfillment.transition(orderId,OrderAction.DELIVER,new FulfillmentRequest(2L,2L,new BigDecimal("115000")));
                return true;
            } catch(ApplicationException ex) {
                assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFLICT); return false;
            } finally {SecurityContextHolder.clearContext();}
        };
        try {
            var first=pool.submit(attempt); var second=pool.submit(attempt); start.countDown();
            assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND to_status='DELIVERED'",Long.class,orderId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?",Long.class,orderId)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?",String.class,orderId)).isEqualTo("PAID");
        } finally {pool.shutdownNow();}
    }

    @Test void multiRoleVendorWaitsForOrderWithoutBlockingShipperEligibility() throws Exception {
        // The same persisted account owns the shop and is assigned to its shipment.
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='VENDOR'", shipper);
        jdbc.update("UPDATE uteexpress.shops SET owner_id=? WHERE id=(SELECT shop_id FROM uteexpress.orders WHERE id=?)", shipper, orderId);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        var vendorPid = new java.util.concurrent.atomic.AtomicInteger();
        var started = new java.util.concurrent.CountDownLatch(1);
        var vendorFuture = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try {
            tx.executeWithoutResult(outer -> {
                jdbc.execute("SET LOCAL lock_timeout='3s'");
                int shippingPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, orderId);
                vendorFuture.set(pool.submit(() -> {
                    authenticate(shipper, "VENDOR");
                    try {
                        assertThatThrownBy(() -> tx.executeWithoutResult(inner -> {
                            jdbc.execute("SET LOCAL lock_timeout='10s'");
                            vendorPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            started.countDown();
                            orderLifecycle.transition(
                                    new com.uteexpress.order.dto.OrderTransitionCommand(orderId,
                                            com.uteexpress.order.dto.OrderStatus.CONFIRMED, 0L,
                                            OrderAction.CANCEL_CONFIRMED, "UNABLE_TO_FULFILL"));
                        })).isInstanceOfSatisfying(ApplicationException.class,
                                ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFLICT));
                    } finally { SecurityContextHolder.clearContext(); }
                }));
                try {
                    assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                    boolean blocked = false;
                    while (System.nanoTime() < deadline) {
                        blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                                "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, shippingPid, vendorPid.get()));
                        if (blocked) break;
                        java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10));
                    }
                    assertThat(blocked).as("Vendor is waiting for the held Order row").isTrue();
                    // Old Account -> Order ordering held this account FOR UPDATE and deadlocked here.
                    authenticate(shipper, "SHIPPER");
                    fulfillment.transition(orderId, OrderAction.PICK_UP, new FulfillmentRequest(0L, 0L, null));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); throw new AssertionError(ex);
                }
            });
            vendorFuture.get().get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, orderId)).isEqualTo("PICKED_UP");
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?", String.class, orderId)).isEqualTo("PICKED_UP");
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?", String.class, orderId)).isEqualTo("UNPAID");
        } finally { pool.shutdownNow(); }
    }

    private List<Object> state() {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE id=?",orderId),
                jdbc.queryForList("SELECT * FROM uteexpress.shipments WHERE order_id=?",orderId),
                jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?",orderId),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id",orderId),
                jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) ORDER BY id",orderId));
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
