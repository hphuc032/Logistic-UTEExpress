package com.uteexpress.governance;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.governance.service.AdminRevenueService;
import com.uteexpress.reporting.dto.*;
import com.uteexpress.reporting.service.RevenueProjectionService;
import com.uteexpress.order.service.RevenueService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(AdminRevenueIT.Configuration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminRevenueIT {
    @Container @ServiceConnection static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminRevenueService adminRevenue;
    @Autowired RevenueService vendorRevenue;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean RevenueProjectionService projections;
    @MockitoSpyBean NamedParameterJdbcTemplate named;
    long admin,vendor,otherVendor,buyer,shop,otherShop,product,secondProduct;
    static final Instant DELIVERY=Instant.parse("2027-01-01T01:00:00Z");
    @BeforeEach void setup() {
        admin=account("ADMIN");vendor=account("VENDOR");otherVendor=account("VENDOR");buyer=account("USER");
        shop=shop(vendor);otherShop=shop(otherVendor);
        long category=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Financial',?,true) RETURNING id",Long.class,UUID.randomUUID().toString());
        product=product(shop,category,"Snapshot");secondProduct=product(shop,category,"Second");auth(admin,"ADMIN");
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private RevenueMetrics selected(String from,String to,Long scope){return adminRevenue.dashboard(LocalDate.parse(from),LocalDate.parse(to),scope).selected().metrics();}
    private static void money(RevenueAmount value,String expected){assertThat(value.complete()).isTrue();assertThat(value.value()).isEqualByComparingTo(expected);}
    private static UteExpressPrincipal principal(long id,String role){return new UteExpressPrincipal(id,"display-not-an-id",null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true);}
    private static void auth(long id,String role){var p=principal(id,role);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    @TestConfiguration static class Configuration { @Bean @Primary Clock adminRevenueClock(){return Clock.fixed(Instant.parse("2027-01-01T01:30:00Z"),ZoneOffset.UTC);} }

    @Test @Order(1) void multiShopScopeSharesVendorFactsWithoutItemOrPaymentFanout() throws Exception {
        long a=order(shop,DELIVERY,DELIVERY),b=order(shop,DELIVERY,DELIVERY);order(otherShop,DELIVERY,DELIVERY);
        unsuccessfulPayment(a,1,DELIVERY.minusSeconds(60));unsuccessfulPayment(a,2,DELIVERY.plusSeconds(60));
        for(int i=0;i<7;i++) item(b,product,1,1);
        var all=adminRevenue.dashboard(null,null,null).month().metrics();
        money(all.deliveredSales(),"270000");money(all.collectedPayments(),"270000");money(all.netCommission(),"27000");money(all.netRevenueAfterCommission(),"243000");
        var scoped=adminRevenue.dashboard(null,null,shop).month().metrics();money(scoped.netCollectedRevenue(),"180000");
        auth(vendor,"VENDOR");assertThat(vendorRevenue.dashboard(null,null).month().metrics()).isEqualTo(scoped);
        auth(admin,"ADMIN");
        mvc.perform(get("/admin/dashboard/revenue/metrics").param("shopId",String.valueOf(shop)).with(user(principal(admin,"ADMIN"))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.month.metrics.netRevenueAfterCommission.value").value(162000));
        mvc.perform(get("/admin/dashboard/revenue").with(user(principal(admin,"ADMIN"))))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Net Revenue After Commission")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("243.000")));
        auth(admin,"ADMIN");var zero=adminRevenue.dashboard(null,null,Long.MAX_VALUE).month().metrics();money(zero.netCollectedRevenue(),"0");
    }
    @Test void collectionDeliveryReceiptRefundUseIndependentPeriodsAndOriginalCommission() {
        long id=order(shop,DELIVERY,Instant.parse("2026-12-31T16:59:59.999999Z"));
        receipt(id,Instant.parse("2027-02-01T00:00:00Z"));refund(id,Instant.parse("2027-03-01T00:00:00Z"));
        money(selected("2026-12-01","2027-01-01",shop).netCollectedRevenue(),"90000");
        money(selected("2027-01-01","2027-02-01",shop).netRevenueAfterCommission(),"-9000");
        money(selected("2027-02-01","2027-03-01",shop).netCollectedRevenue(),"0");
        var march=selected("2027-03-01","2027-04-01",shop);money(march.refunds(),"90000");money(march.netCommission(),"-9000");money(march.netRevenueAfterCommission(),"-81000");
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (99,'2027-01-01',?)",admin);
        assertThat(selected("2027-03-01","2027-04-01",shop)).isEqualTo(march);
    }
    @ParameterizedTest @ValueSource(strings={"missingPayment","invalidPaid","commission","refundReference","legacyRefund"})
    void unknownEvidenceInOneShopDoesNotBecomeKnownZeroOrContaminateAnotherScope(String kind) {
        order(shop,DELIVERY,DELIVERY);long bad=order(otherShop,DELIVERY,DELIVERY);
        switch(kind){
            case "missingPayment"->jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?",bad);
            case "invalidPaid"->jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=?",bad);
            case "commission"->jdbc.update("UPDATE uteexpress.orders SET commission_amount=9000.50 WHERE id=?",bad);
            case "refundReference"->{receipt(bad,DELIVERY.plusSeconds(1));long wrong=unsuccessfulPayment(bad,105000,DELIVERY);refundUsingPayment(bad,DELIVERY.plusSeconds(2),0,wrong);}
            default->jdbc.update("UPDATE uteexpress.orders SET status='REFUNDED' WHERE id=?",bad);
        }
        var badScope=adminRevenue.dashboard(null,null,otherShop).month().metrics();assertThat(badScope.netRevenueAfterCommission().value()).isNull();
        var all=adminRevenue.dashboard(null,null,null).month().metrics();assertThat(all.netRevenueAfterCommission().value()).isNull();assertThat(all.netRevenueAfterCommission().unknownEvents()).isPositive();
        money(adminRevenue.dashboard(null,null,shop).month().metrics().netRevenueAfterCommission(),"81000");
    }
    @Test void nullPolicyWithLegitimateZeroCommissionRemainsAvailable() {
        long id=order(shop,DELIVERY,DELIVERY);jdbc.update("UPDATE uteexpress.orders SET commission_policy_id=NULL,commission_amount=0,commission_rate_snapshot=0 WHERE id=?",id);
        money(adminRevenue.dashboard(null,null,shop).month().metrics().netRevenueAfterCommission(),"90000");
    }
    @ParameterizedTest @ValueSource(strings={"USER","VENDOR","SHIPPER","MANAGER"})
    void nonAdminCannotReadFinancialHtmlJsonOrService(String role) throws Exception {
        long id=account(role);auth(id,role);
        for(String route:List.of("/admin/dashboard/revenue","/admin/dashboard/revenue/metrics")) mvc.perform(get(route).with(user(principal(id,role)))).andExpect(status().isForbidden());
        auth(id,role);assertThatThrownBy(()->adminRevenue.dashboard(null,null,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void forgedRoleLockedOrRevokedAdminIsDeniedFromPersistedFacts() throws Exception {
        long ordinary=account("USER");
        for(long id:List.of(ordinary,admin)) {
            if(id==admin)jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",id);
            mvc.perform(get("/admin/dashboard/revenue/metrics").with(user(principal(id,"ADMIN")))).andExpect(status().isForbidden());
        }
    }
    @ParameterizedTest @ValueSource(strings={"shopId=-1","shopId=1&shopId=2","role=ADMIN","from=2027-02-01","from=2027-02-01&toExclusive=2027-01-01","from=bad","from=2025-01-01&toExclusive=2027-01-01"})
    void invalidOrUntrustedFiltersAreRejected(String query) throws Exception {
        mvc.perform(get("/admin/dashboard/revenue/metrics?"+query).with(user(principal(admin,"ADMIN")))).andExpect(status().isBadRequest());
    }
    @Test void consistentSnapshotSurvivesConcurrentPaymentChangeAndRevocationButNextReadDenies() throws Exception {
        long id=order(shop,DELIVERY,DELIVERY);var count=new AtomicInteger();var pool=Executors.newSingleThreadExecutor();
        var target=(RevenueProjectionService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(projections);
        doAnswer(call->{
            assertThat(jdbc.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("repeatable read");
            assertThat(jdbc.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");
            Object result=call.callRealMethod();
            if(count.incrementAndGet()==1)pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(tx->{
                jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=?",id);
                jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",admin);
            })).get(10,TimeUnit.SECONDS);
            return result;
        }).when(target).metrics(any(RevenueScope.class),any());
        try {var result=adminRevenue.dashboard(LocalDate.of(2027,1,1),LocalDate.of(2027,1,3),shop);
            money(result.today().metrics().netCollectedRevenue(),"90000");money(result.selected().metrics().netCollectedRevenue(),"90000");
            assertThatThrownBy(()->adminRevenue.dashboard(null,null,shop)).isInstanceOf(ApplicationException.class);
        } finally {pool.shutdownNow();}
    }
    @Test void analyticalQueryCountDoesNotScaleWithShopOrItemCountAndOuterTransactionIsRejected() {
        for(int i=0;i<5;i++)order(i%2==0?shop:otherShop,DELIVERY,DELIVERY);
        clearInvocations(named);adminRevenue.dashboard(LocalDate.of(2027,1,1),LocalDate.of(2027,1,3),null);
        verify(named,times(3)).queryForObject(eq(RevenueProjectionService.GLOBAL_METRICS_SQL),any(SqlParameterSource.class),any(RowMapper.class));
        new TransactionTemplate(manager).executeWithoutResult(tx->assertThatThrownBy(()->adminRevenue.dashboard(null,null,null)).isInstanceOf(ApplicationException.class));
    }
    // Pristine global numeric cases precede deliberately undated legacy fixtures.
    // Legacy diagnostics intentionally affect all periods. Each bad fixture is also asserted by its own Shop.
    @Test @Order(2) void globalReportingUsesHalfOpenHcmDayBoundaries() {
        Instant lower=Instant.parse("2026-06-30T17:00:00Z"), upper=Instant.parse("2026-07-01T17:00:00Z");
        order(shop,lower.minusNanos(1000),lower.minusNanos(1000));
        order(shop,lower,lower);order(otherShop,upper.minusNanos(1000),upper.minusNanos(1000));order(otherShop,upper,upper);
        money(selected("2026-07-01","2026-07-02",null).netCollectedRevenue(),"180000");
    }
    @Test void projectionsRequireAnExistingReadOnlyRepeatableSnapshotAndExplicitScope() {
        var period=new RevenuePeriod(LocalDate.of(2027,1,1),LocalDate.of(2027,1,2));
        assertThatThrownBy(()->projections.metrics(RevenueScope.allShops(),period)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        new TransactionTemplate(manager).executeWithoutResult(tx->{assertThatThrownBy(()->projections.metrics(RevenueScope.allShops(),period)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);tx.setRollbackOnly();});
        assertThatThrownBy(()->new RevenueScope(RevenueScope.Kind.SHOP,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RevenueScope(RevenueScope.Kind.ALL_SHOPS,shop)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void anonymousAndRevokedAdminCannotReadAndManagerUiHasNoFinancialLink() throws Exception {
        mvc.perform(get("/admin/dashboard/revenue/metrics")).andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",admin);
        mvc.perform(get("/admin/dashboard/revenue").with(user(principal(admin,"ADMIN")))).andExpect(status().isForbidden());
        long managerId=account("MANAGER");
        mvc.perform(get("/manager/dashboard").with(user(principal(managerId,"MANAGER")))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/admin/dashboard/revenue"))));
    }

    @Test void explainActualGlobalAndShopQueriesOnRepresentativeFixture() throws Exception {
        String prefix="PLAN-"+UUID.randomUUID();
        jdbc.update("""
            INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,
                subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot,created_at,delivered_at)
            SELECT ?||g,?||g,'plan',?,CASE WHEN g%2=0 THEN ? ELSE ? END,'DELIVERED','Buyer','0900000000','VN','District','Address',
                100000,10000,15000,105000,9000,10,'2026-01-01'::timestamptz+(g%365)*interval '1 day',
                '2026-01-01'::timestamptz+(g%365)*interval '1 day'+interval '1 minute' FROM generate_series(1,1200) g
            """,prefix,prefix,buyer,shop,otherShop);
        jdbc.update("""
            INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at)
            SELECT id,'COD','PAID',grand_total,order_code,delivered_at-interval '1 second' FROM uteexpress.orders WHERE order_code LIKE ?
            """,prefix+"%");
        jdbc.update("""
            INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at,expired_at)
            SELECT id,'ONLINE','UNPAID',1,'FAILED-'||order_code,delivered_at-interval '2 days',delivered_at-interval '1 day'
            FROM uteexpress.orders WHERE order_code LIKE ?
            """,prefix+"%");
        jdbc.update("""
            INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id,created_at)
            SELECT id,'SHIPPING','DELIVERED',?,delivered_at FROM uteexpress.orders WHERE order_code LIKE ?
            """,vendor,prefix+"%");
        jdbc.update("""
            INSERT INTO uteexpress.order_items(order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,final_unit_price,quantity,line_total)
            SELECT o.id,?,'Plan snapshot',CASE WHEN g=3 THEN 20000 ELSE 40000 END,0,
                CASE WHEN g=3 THEN 20000 ELSE 40000 END,1,CASE WHEN g=3 THEN 20000 ELSE 40000 END
            FROM uteexpress.orders o CROSS JOIN generate_series(1,3) g WHERE order_code LIKE ?
            """,product,prefix+"%");
        // 120 whole receipts/refunds, distributed across the two shops. No production trigger is disabled.
        var ids=jdbc.queryForList("SELECT id FROM uteexpress.orders WHERE order_code LIKE ? ORDER BY id",Long.class,prefix+"%");
        for(int i=0;i<ids.size();i+=10) {
            long id=ids.get(i+(i/10)%2); Instant delivered=jdbc.queryForObject("SELECT delivered_at FROM uteexpress.orders WHERE id=?",java.time.OffsetDateTime.class,id).toInstant();
            receipt(id,delivered.plusSeconds(86400)); refund(id,delivered.plusSeconds(172800));
        }
        for(String table:List.of("orders","payments","order_items","order_status_history","return_requests","refunds")) jdbc.execute("ANALYZE uteexpress."+table);
        var output=new StringBuilder(jdbc.queryForObject("SELECT version()",String.class))
            .append("\nAdded representative fixture: 1200 Orders across 2 Shops, 3600 Items, 2400 Payments (1200 PAID + 1200 UNPAID), 120 Receipts, 120 Refunds. No new indexes.\n")
            .append("Fixture scope: 600 Orders in the selected Shop. Other tests may also have rows in this disposable database.\n");
        for(String table:List.of("orders","payments","order_items","order_status_history","return_requests","refunds"))
            output.append("database rows ").append(table).append('=').append(jdbc.queryForObject("SELECT count(*) FROM uteexpress."+table,Long.class)).append('\n');
        for(var window:List.of(new RevenuePeriod(LocalDate.of(2026,1,1),LocalDate.of(2026,1,2)),
                new RevenuePeriod(LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)))) {
            var args=RevenueProjectionService.parameters(shop,window);
            for(String sql:List.of(RevenueProjectionService.METRICS_SQL,RevenueProjectionService.GLOBAL_METRICS_SQL)) {
                named.queryForList(sql,args); // warm-up
                var plan=named.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) "+sql,args,String.class);
                output.append("\nquery=").append(sql.equals(RevenueProjectionService.METRICS_SQL) ? "METRICS_SQL":"GLOBAL_METRICS_SQL")
                    .append(" shop=").append(shop).append(" start=").append(window.start()).append(" end=").append(window.end()).append('\n')
                    .append(String.join("\n",plan)).append('\n');
                assertThat(String.join("\n",plan)).contains("Execution Time","Buffers:");
            }
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/dash02-financial-explain.txt"),output.toString());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/dash02-financial-explain-queries.sql"),RevenueProjectionService.METRICS_SQL+";\n"+RevenueProjectionService.GLOBAL_METRICS_SQL+";\n");
        System.out.println("DASH-02 financial EXPLAIN evidence: target/dash02-financial-explain.txt");
    }
    private long account(String role) {
        String name="dash"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?, 'test','ACTIVE') RETURNING id",Long.class,name+"@test.invalid",name+"@test.invalid",name,name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role); return id;
    }
    private long shop(long owner) { return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Revenue shop',?,'Pickup','APPROVED') RETURNING id",Long.class,owner,UUID.randomUUID().toString()); }
    private long product(long shopId,long category,String name) { return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,?,40000,100,'ACTIVE') RETURNING id",Long.class,shopId,category,name); }
    private long order(long ownedShop,Instant delivered,Instant paid) {
        String key=UUID.randomUUID().toString();
        long id=jdbc.queryForObject("""
            INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,
                subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot,created_at,delivered_at)
            VALUES (?,?,'fixture',?,?,?,'Buyer','0900000000','VN','District','Address',100000,10000,15000,105000,9000,10,?,?) RETURNING id
            """,Long.class,key,key,buyer,ownedShop,delivered==null ? "NEW":"DELIVERED",ts(DELIVERY.minusSeconds(60)),ts(delivered));
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at) VALUES (?,'COD',?,105000,?,?)",id,paid==null ? "UNPAID":"PAID",key,ts(paid));
        if(delivered!=null) history(id,"SHIPPING","DELIVERED",delivered,vendor);
        item(id,product,2,40000); item(id,secondProduct,1,20000); return id;
    }
    private void item(long id,long p,int qty,int price) {
        jdbc.update("INSERT INTO uteexpress.order_items(order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,final_unit_price,quantity,line_total) VALUES (?,?,?, ?,0,?,?,?)",id,p,p==product ? "Snapshot <script>":"Second",price,price,qty,price*qty);
    }
    private void history(long id,String from,String to,Instant at,long actor) {
        jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id,created_at) VALUES (?,?,?,?,?)",id,from,to,actor,ts(at));
    }
    private void receipt(long id,Instant at) {
        jdbc.update("""
            INSERT INTO uteexpress.return_requests(order_id,requested_by,reason,status,requested_at,decided_by,decision_at,received_by,received_at,receipt_reference,created_at,updated_at)
            VALUES (?,?,'Wrong','APPROVED',?,?,?,?,?,'PROOF',?,?)
            """,id,buyer,ts(at.minusSeconds(2)),vendor,ts(at.minusSeconds(1)),vendor,ts(at),ts(at.minusSeconds(2)),ts(at));
        history(id,"RETURN_REQUESTED","RETURNED",at,vendor); jdbc.update("UPDATE uteexpress.orders SET status='RETURNED' WHERE id=?",id);
    }
    private void refund(long id,Instant at) { refund(id,at,0); }
    private void refund(long id,Instant at,int amountDifference) {
        long paid=jdbc.queryForObject("SELECT id FROM uteexpress.payments WHERE order_id=? AND status='PAID'",Long.class,id);
        refundUsingPayment(id,at,amountDifference,paid);
    }
    private void refundUsingPayment(long id,Instant at,int amountDifference,long paymentId) {
        jdbc.update("""
            INSERT INTO uteexpress.refunds(order_id,payment_id,return_request_id,processed_by,operation,idempotency_key,schema_version,
                expected_order_version,expected_return_request_version,canonical_payload,payload_sha256,amount,status,proof_reference,
                server_timestamp,created_at,result_order_version,payment_paid_at,return_received_at)
            SELECT o.id,p.id,t.id,?,'PAY02_MANUAL_COD_REFUND_V1',?,1,0,0,decode('01','hex'),decode(repeat('00',32),'hex'),o.grand_total-?,
                'SUCCEEDED','PROOF',?,?,1,p.paid_at,t.received_at FROM uteexpress.orders o JOIN uteexpress.payments p ON p.id=?
                JOIN uteexpress.return_requests t ON t.order_id=o.id WHERE o.id=?
            """,admin,UUID.randomUUID().toString(),amountDifference,ts(at),ts(at),paymentId,id);
        jdbc.update("UPDATE uteexpress.orders SET status='REFUNDED' WHERE id=?",id);
    }
    private long successfulPayment(long id,Instant paidAt) {
        return jdbc.queryForObject("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at) VALUES (?,'COD','PAID',105000,?,?) RETURNING id",
            Long.class,id,UUID.randomUUID().toString(),ts(paidAt));
    }
    private void poisonInitialUnpaidAttempt(long id) {
        jdbc.update("UPDATE uteexpress.payments SET method='ONLINE',amount=1,paid_at='2026-01-01',expired_at='2026-01-02' WHERE order_id=? AND status='UNPAID'",id);
    }
    private long unsuccessfulPayment(long id,int amount,Instant at) {
        return jdbc.queryForObject("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at,expired_at) VALUES (?,'ONLINE','UNPAID',?,?,?,?) RETURNING id",
            Long.class,id,amount,UUID.randomUUID().toString(),ts(at),ts(at));
    }
    private static Timestamp ts(Instant at) { return at==null ? null:Timestamp.from(at); }
}
