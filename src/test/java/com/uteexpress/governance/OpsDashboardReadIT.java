package com.uteexpress.governance;

import com.uteexpress.governance.dto.*;
import com.uteexpress.governance.service.OpsDashboardReadService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.payment.dto.ManualRefundCommand;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
@Import(OpsDashboardReadIT.Configuration.class)
@Timeout(40)
class OpsDashboardReadIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    static final Instant AT = Instant.parse("2001-01-15T02:00:00Z");
    static final LocalDate FROM = LocalDate.parse("2001-01-01"), TO = LocalDate.parse("2001-01-31");
    @TestConfiguration(proxyBeanMethods = false) static class Configuration {
        @Bean @Primary Clock dashboardClock() { return Clock.fixed(AT, ZoneOffset.UTC); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired OpsDashboardReadService reports;
    @Autowired OrderLifecycleService lifecycle;
    @MockitoSpyBean NamedParameterJdbcTemplate named;
    long admin, manager, buyer, vendor, shop;
    @BeforeEach void fixture() {
        admin = account("ADMIN"); manager = account("MANAGER"); buyer = account("USER"); vendor = account("VENDOR");
        shop = shop(vendor, AT); auth(admin, "ADMIN");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void aggregatesSeparateStreamsAndShopScopeWithoutItemFanout() throws Exception {
        long first = order(shop, "CONFIRMED", AT); order(shop, "NEW", AT);
        long older = order(shop, "CONFIRMED", Instant.parse("2000-12-30T00:00:00Z"));
        long other = shop(account("VENDOR"), AT); shipment(order(other, "CONFIRMED", AT), AT);
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Dashboard',?,true) RETURNING id", Long.class, UUID.randomUUID().toString());
        for (int i = 0; i < 8; i++) {
            int price = i == 7 ? 30 : 10;
            long product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Item',?,10,'ACTIVE') RETURNING id", Long.class, shop, category, price);
            jdbc.update("INSERT INTO uteexpress.order_items(order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,final_unit_price,quantity,line_total) VALUES (?,?,'Item',?,0,?,1,?)", first, product, price, price, price);
        }
        shipment(first, AT);
        shipment(older, AT);
        shipment(order(shop, "CONFIRMED", Instant.parse("2000-12-29T00:00:00Z")), Instant.parse("2000-12-31T16:59:59Z"));
        var view = reports.admin(FROM, TO, shop);
        assertThat(view.orders().total()).isEqualTo(2);
        assertThat(view.orders().states()).containsExactly(new OpsDashboardMetrics.StateCount("CONFIRMED", 1), new OpsDashboardMetrics.StateCount("NEW", 1));
        assertThat(view.shipments().total()).isEqualTo(2);
        assertThat(view.shops().total()).isEqualTo(1);
        assertThat(view.succeededRefunds()).isZero();
        mvc.perform(get("/admin/dashboard").with(user(principal(admin, "ADMIN"))).param("from", FROM.toString()).param("to", TO.toString()).param("shopId", Long.toString(shop)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đơn tạo trong kỳ")))
                .andExpect(content().string(not(containsString("Chưa có dữ liệu tổng quan"))))
                .andExpect(content().string(containsString("/admin/audit-logs")));
        mvc.perform(get("/manager/dashboard").with(user(principal(manager, "MANAGER"))))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("href=\"/admin/audit-logs\""))));
    }

    @Test void localDateBoundsIncludeLowerButExcludeUpperIndependentOfJvmTimezone() throws Exception {
        Instant lower = Instant.parse("2000-12-31T17:00:00Z"), upper = Instant.parse("2001-01-01T17:00:00Z");
        order(shop, "NEW", lower.minusNanos(1000)); order(shop, "NEW", lower);
        order(shop, "NEW", upper.minusNanos(1000)); order(shop, "NEW", upper);
        var view = reports.admin(FROM, FROM, shop);
        assertThat(view.orders().total()).isEqualTo(2);
        assertThat(view.window().start()).isEqualTo(lower); assertThat(view.window().end()).isEqualTo(upper);
        mvc.perform(get("/admin/dashboard/metrics").with(user(principal(admin, "ADMIN")))
                .param("from", FROM.toString()).param("to", FROM.toString()).param("shopId", Long.toString(shop)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.orders.total").value(2)).andExpect(jsonPath("$.window.timezone").value("Asia/Ho_Chi_Minh"));
    }

    @Test void successfulRefundCountsInCompletionPeriodAndReadDoesNotMutateEvidence() {
        long order = order(shop, "DELIVERED", Instant.parse("2000-12-30T00:00:00Z"));
        jdbc.update("UPDATE uteexpress.orders SET delivered_at=? WHERE id=?", java.sql.Timestamp.from(AT.minusSeconds(60)), order);
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at) VALUES (?,'COD','PAID',100,?,?)", order, UUID.randomUUID().toString(), java.sql.Timestamp.from(AT.minusSeconds(60)));
        auth(buyer, "USER"); lifecycle.requestReturn(new RequestReturnCommand(order, 0L, "Return all"));
        auth(vendor, "VENDOR"); lifecycle.approveReturn(new ReturnDecisionCommand(order, 1L, 0L, null));
        lifecycle.receiveReturn(new ReceiveReturnCommand(order, 1L, 1L, "Receipt"));
        auth(admin, "ADMIN"); lifecycle.completeRefund(new ManualRefundCommand(order, 2L, 2L, "Proof", UUID.randomUUID().toString()));
        var before = evidence(order);
        var view = reports.admin(FROM, TO, shop);
        assertThat(view.orders().total()).isZero(); // Order was created before reporting window.
        assertThat(view.succeededRefunds()).isEqualTo(1);
        assertThat(reports.admin(LocalDate.parse("2000-12-01"), LocalDate.parse("2000-12-31"), shop).succeededRefunds()).isZero();
        assertThat(evidence(order)).isEqualTo(before);
    }

    @Test void auditFiltersPaginationAndProjectionExcludePrivateSummaries() throws Exception {
        String action = action();
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 25; i++) ids.add(audit(action, admin, shop, AT));
        var filter = new OpsAuditFilter(action, "SHOP", admin, shop, 0);
        var first = reports.audit(FROM, TO, filter);
        assertThat(first.totalElements()).isEqualTo(25); assertThat(first.content()).hasSize(20); assertThat(first.hasNext()).isTrue();
        assertThat(first.content().stream().map(OpsAuditPage.Entry::id).toList()).containsExactlyElementsOf(ids.reversed().subList(0, 20));
        var second = reports.audit(FROM, TO, new OpsAuditFilter(action, "SHOP", admin, shop, 1));
        assertThat(second.content()).hasSize(5); assertThat(second.hasPrevious()).isTrue(); assertThat(second.hasNext()).isFalse();
        mvc.perform(get("/admin/audit-logs").with(user(principal(admin, "ADMIN"))).param("from", FROM.toString()).param("to", TO.toString()).param("action", action))
                .andExpect(status().isOk()).andExpect(content().string(containsString(action)))
                .andExpect(content().string(not(containsString("PRIVATE_SUMMARY"))));
        mvc.perform(get("/admin/audit-logs/data").with(user(principal(admin, "ADMIN"))).param("from", FROM.toString()).param("to", TO.toString()).param("action", action))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(content().string(not(containsString("beforeSummary"))));
    }

    @Test void nullActorAndEmptyDatasetAreHonestAndMissingShopIsEmpty() {
        String action = action(); audit(action, null, shop, AT);
        assertThat(reports.audit(FROM, TO, new OpsAuditFilter(action, null, null, null, 0)).content().getFirst().actorId()).isNull();
        assertThat(reports.audit(FROM, TO, new OpsAuditFilter(action(), null, null, null, 0)).totalElements()).isZero();
        assertThat(reports.admin(FROM, TO, Long.MAX_VALUE).orders().total()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "VENDOR", "SHIPPER", "MANAGER"})
    void onlyAdminCanReadAudit(String role) throws Exception {
        long actor = account(role);
        mvc.perform(get("/admin/audit-logs/data").with(user(principal(actor, role)))).andExpect(status().isForbidden());
        auth(actor, role);
        assertThatThrownBy(() -> reports.audit(FROM, TO, new OpsAuditFilter(null, null, null, null, 0)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void roleAssertionsCannotOverridePersistedRoleOrInactiveAccount() throws Exception {
        long ordinary = account("USER");
        mvc.perform(get("/admin/dashboard/metrics").with(user(principal(ordinary, "ADMIN")))).andExpect(status().isForbidden());
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", admin);
        mvc.perform(get("/admin/dashboard/metrics").with(user(principal(admin, "ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/dashboard/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/manager/dashboard/metrics").with(user(principal(ordinary, "USER")))).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"from=bad-date", "from=2001-02-01&to=2001-01-01", "from=2000-01-01&to=2002-01-01", "shopId=-1", "shopId=1&shopId=2", "role=ADMIN", "sort=DROP_TABLE"})
    void invalidOrAmbiguousDashboardFiltersAreRejected(String query) throws Exception {
        mvc.perform(get("/admin/dashboard/metrics?" + query).with(user(principal(admin, "ADMIN")))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings = {"page=501", "actorId=0", "targetId=-1", "action=lowercase", "targetType=bad-type", "page=0&page=1", "size=10000"})
    void invalidAuditFiltersAreRejected(String query) throws Exception {
        mvc.perform(get("/admin/audit-logs/data?" + query).with(user(principal(admin, "ADMIN")))).andExpect(status().isBadRequest());
    }

    @Test void auditCountAndPageUseReadOnlyRepeatableSnapshotDuringConcurrentAppend() throws Exception {
        String action = action(); var ids = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) ids.add(audit(action, admin, shop, AT));
        var counted = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            Object count = invocation.callRealMethod(); counted.countDown();
            if (!release.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("count release timed out");
            return count;
        }).when(named).queryForObject(startsWith("SELECT count(*) FROM uteexpress.audit_logs"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(Long.class));
        var pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit(() -> {
                auth(admin, "ADMIN");
                try { return reports.audit(FROM, TO, new OpsAuditFilter(action, null, null, null, 0)); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(counted.await(5, TimeUnit.SECONDS)).isTrue();
            long concurrent = audit(action, admin, shop, AT);
            release.countDown();
            var page = result.get(8, TimeUnit.SECONDS);
            assertThat(page.totalElements()).isEqualTo(20);
            assertThat(page.content().stream().map(OpsAuditPage.Entry::id).toList()).doesNotContain(concurrent).containsExactlyElementsOf(ids.reversed());
        } finally { release.countDown(); pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS); }
    }

    @Test void aggregateQueryCountStaysConstantAndPlansAreRecordedForIsolatedFixture() {
        clearInvocations(named);
        reports.admin(FROM, TO, shop);
        assertThat(reportQueries()).hasSize(5);
        for (int i = 0; i < 100; i++) order(shop, "NEW", AT);
        clearInvocations(named);
        assertThat(reports.admin(FROM, TO, shop).orders().total()).isEqualTo(100);
        var queries = reportQueries();
        assertThat(queries).hasSize(5);
        for (var invocation : queries) {
            String sql = (String) invocation.getArgument(0);
            org.springframework.jdbc.core.namedparam.SqlParameterSource params = invocation.getArgument(1);
            var plan = named.query("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql, params, (rs, ignored) -> rs.getString(1));
            assertThat(String.join("\n", plan)).contains("Execution Time:");
            System.out.println("DASH-02 isolated 100-order fixture plan: " + sql + "\n" + String.join("\n", plan));
        }
    }
    private List<org.mockito.invocation.Invocation> reportQueries() {
        return mockingDetails(named).getInvocations().stream().filter(invocation -> {
            Object[] args = invocation.getArguments();
            return args.length == 3 && args[0] instanceof String sql && sql.startsWith("SELECT")
                    && args[1] instanceof org.springframework.jdbc.core.namedparam.SqlParameterSource
                    && ((invocation.getMethod().getName().equals("query") && args[2] instanceof org.springframework.jdbc.core.RowMapper)
                    || (invocation.getMethod().getName().equals("queryForObject") && args[2] == Long.class));
        }).toList();
    }

    @Test void adminAccountAggregateIsGlobalWhileManagerReceivesNoAccountAggregate() throws Exception {
        long before = reports.admin(FROM, TO, shop).accounts().total();
        jdbc.update("UPDATE uteexpress.users SET created_at=? WHERE id=?", java.sql.Timestamp.from(AT), buyer);
        assertThat(reports.admin(FROM, TO, shop).accounts().total()).isEqualTo(before + 1);
        auth(manager, "MANAGER");
        assertThat(reports.manager(FROM, TO, shop).accounts()).isNull();
        mvc.perform(get("/manager/dashboard/metrics").with(user(principal(manager, "MANAGER"))).param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accounts").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void legacyRefundedStatusWithoutLedgerDoesNotFabricateSuccessfulRefundCount() {
        order(shop, "REFUNDED", AT);
        var view = reports.admin(FROM, TO, shop);
        assertThat(view.orders().states()).containsExactly(new OpsDashboardMetrics.StateCount("REFUNDED", 1));
        assertThat(view.succeededRefunds()).isZero();
    }

    private long account(String role) {
        String name = "dash" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test','ACTIVE') RETURNING id", Long.class, name+"@test.invalid", name+"@test.invalid", name, name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role); return id;
    }
    private long shop(long owner, Instant at) {
        return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status,created_at,updated_at) VALUES (?,'Dashboard shop',?,'Pickup','APPROVED',?,?) RETURNING id",
                Long.class, owner, UUID.randomUUID().toString(), java.sql.Timestamp.from(at), java.sql.Timestamp.from(at));
    }
    private long order(long shop, String status, Instant created) {
        String key = UUID.randomUUID().toString();
        return jdbc.queryForObject("INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot,created_at,updated_at) VALUES (?,?,'hash',?,?,?,'Receiver','0900000000','VN','District','Detail',100,0,0,100,0,0,?,?) RETURNING id",
                Long.class, key, key, buyer, shop, status, java.sql.Timestamp.from(created), java.sql.Timestamp.from(created));
    }
    private void shipment(long order, Instant at) {
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Dashboard carrier',true) RETURNING id", Long.class, "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipments(order_id,provider_id,assigned_shipper_id,status,shipping_service_snapshot,fee_snapshot,created_at,updated_at) VALUES (?,?,?,'ASSIGNED','STANDARD',0,?,?)", order, provider, account("SHIPPER"), java.sql.Timestamp.from(at), java.sql.Timestamp.from(at));
    }
    private long audit(String action, Long actor, long target, Instant at) {
        return jdbc.queryForObject("INSERT INTO uteexpress.audit_logs(actor_id,action,target_type,target_id,before_summary,after_summary,reason,created_at) VALUES (?,?,'SHOP',?,'PRIVATE_SUMMARY','PRIVATE_SUMMARY','DASH_TEST',?) RETURNING id", Long.class, actor, action, target, java.sql.Timestamp.from(at));
    }
    private String action() { return "DASH_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(); }
    private List<Object> evidence(long order) {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE id=?", order), jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?", order),
                jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.id), '[]'::jsonb)::text FROM uteexpress.refunds r WHERE order_id=?", String.class, order), jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=?", order));
    }
    private UteExpressPrincipal principal(long actor, String role) { return new UteExpressPrincipal(actor, "opaque", null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true); }
    private void auth(long actor, String role) { var principal = principal(actor, role); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())); }
}
