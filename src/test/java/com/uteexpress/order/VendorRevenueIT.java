package com.uteexpress.order;

import com.uteexpress.common.exception.*;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.repository.RevenueQueryRepository;
import com.uteexpress.order.service.RevenueService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(VendorRevenueIT.Configuration.class)
class VendorRevenueIT {
    @Container @ServiceConnection static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired RevenueService revenue;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean RevenueQueryRepository repository;
    @MockitoSpyBean NamedParameterJdbcTemplate named;
    long vendor,otherVendor,buyer,shop,otherShop,product,secondProduct;
    static final Instant DELIVERY=Instant.parse("2027-01-01T01:00:00Z");
    @BeforeEach void setup() {
        vendor=account("VENDOR"); otherVendor=account("VENDOR"); buyer=account("USER");
        shop=shop(vendor); otherShop=shop(otherVendor);
        long category=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Revenue',?,true) RETURNING id",Long.class,UUID.randomUUID().toString());
        product=product(shop,category,"Snapshot <script>"); secondProduct=product(shop,category,"Second");
        auth(vendor); clearInvocations(named);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void equalValueOrdersAndMultipleItemsDoNotMultiplyMoneyAndHistoryIsRetained() {
        long a=order(shop,DELIVERY,DELIVERY.minusSeconds(1)); order(shop,DELIVERY,DELIVERY.minusSeconds(1));
        order(otherShop,DELIVERY,DELIVERY.minusSeconds(1));
        jdbc.update("UPDATE uteexpress.orders SET status='RETURN_REQUESTED' WHERE id=?",a);
        var result=revenue.dashboard(null,null); var m=result.month().metrics();
        money(m.deliveredSales(),"180000"); money(m.collectedPayments(),"180000"); money(m.commissionAccrued(),"18000");
        money(m.netVendorSales(),"180000"); assertThat(m.deliveredOrders()).isEqualTo(2);
        assertThat(result.topProducts().products()).extracting(RevenueSummary.Product::netQuantity).containsExactly(4L,2L);
    }
    @Test void independentCollectionDeliveryReceiptAndRefundPeriodsUseOriginalSnapshot() {
        Instant paid=Instant.parse("2026-12-31T16:59:59.999999Z");
        Instant delivered=Instant.parse("2026-12-31T17:00:00Z");
        long id=order(shop,delivered,paid);
        receipt(id,Instant.parse("2027-02-01T00:00:00Z"));
        refund(id,Instant.parse("2027-03-01T00:00:00Z"));
        var december=period("2026-12-01","2027-01-01");
        money(december.metrics().collectedPayments(),"90000"); money(december.metrics().deliveredSales(),"0");
        var january=period("2027-01-01","2027-02-01");
        money(january.metrics().deliveredSales(),"90000"); money(january.metrics().collectedPayments(),"0");
        money(january.metrics().commissionAccrued(),"9000");
        var feb=revenue.dashboard(LocalDate.of(2027,2,1),LocalDate.of(2027,3,1));
        money(feb.selected().metrics().refunds(),"0");
        assertThat(feb.topProducts().products()).extracting(RevenueSummary.Product::netQuantity).containsExactly(-1L,-2L);
        var march=revenue.dashboard(LocalDate.of(2027,3,1),LocalDate.of(2027,4,1));
        money(march.selected().metrics().refunds(),"90000"); money(march.selected().metrics().commissionReversed(),"9000");
        money(march.selected().metrics().netVendorSales(),"-90000"); money(march.selected().metrics().netCommission(),"-9000");
        assertThat(march.topProducts().products()).isEmpty();
        money(period("2027-01-01","2027-02-01").metrics().deliveredSales(),"90000");
    }
    @Test void samePeriodFullRefundAndReceiptCancelOnlyTheirOwnMetrics() {
        long id=order(shop,DELIVERY,DELIVERY.minusSeconds(1)); receipt(id,DELIVERY.plusSeconds(1)); refund(id,DELIVERY.plusSeconds(2));
        var result=revenue.dashboard(null,null); var m=result.month().metrics();
        money(m.deliveredSales(),"90000"); money(m.collectedPayments(),"90000"); money(m.refunds(),"90000");
        money(m.netVendorSales(),"0"); money(m.netCommission(),"0");
        assertThat(result.topProducts().products()).allSatisfy(p -> assertThat(p.netQuantity()).isZero());
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?",String.class,id)).isEqualTo("PAID");
    }
    @Test void nullPolicyAndLegitimateZeroRemainCompleteEvenWhenCurrentPolicyChanges() {
        long id=order(shop,DELIVERY,DELIVERY);
        jdbc.update("UPDATE uteexpress.orders SET commission_policy_id=NULL,commission_amount=0,commission_rate_snapshot=0 WHERE id=?",id);
        var first=revenue.dashboard(null,null); money(first.month().metrics().commissionAccrued(),"0");
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (99,'2027-01-01',?)",vendor);
        money(revenue.dashboard(null,null).month().metrics().commissionAccrued(),"0");
    }
    @ParameterizedTest @ValueSource(strings={"commission","paymentAmount","missingPayment","missingPaidTime","deliveryHistory","deliveryTime","ambiguous","expired","online"})
    void invalidEvidenceDoesNotBecomeACompleteZero(String stage) {
        long id=order(shop,DELIVERY,DELIVERY);
        switch(stage) {
            case "commission" -> jdbc.update("UPDATE uteexpress.orders SET commission_amount=9000.50 WHERE id=?",id);
            case "paymentAmount" -> jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=?",id);
            case "missingPayment" -> jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?",id);
            case "missingPaidTime" -> jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=?",id);
            case "deliveryHistory" -> jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=?",id);
            case "deliveryTime" -> jdbc.update("UPDATE uteexpress.orders SET delivered_at=NULL WHERE id=?",id);
            case "ambiguous" -> jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',105000,?)",id,UUID.randomUUID().toString());
            case "expired" -> jdbc.update("UPDATE uteexpress.payments SET expired_at=paid_at WHERE order_id=?",id);
            case "online" -> jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?",id);
        }
        var m=revenue.dashboard(null,null).month().metrics();
        assertThat(m.commissionAccrued().complete()).isFalse(); assertThat(m.commissionAccrued().value()).isNull();
        if(stage.equals("commission")) money(m.deliveredSales(),"90000");
        else { assertThat(m.deliveredSales().value()).isNull(); assertThat(m.netVendorSales().value()).isNull(); }
    }
    @ParameterizedTest @ValueSource(strings={"missingLedger","wrongAmount"})
    void unknownRefundIsNotAZeroAdjustment(String stage) {
        long id=order(shop,DELIVERY,DELIVERY); receipt(id,DELIVERY.plusSeconds(1));
        if(stage.equals("missingLedger")) jdbc.update("UPDATE uteexpress.orders SET status='REFUNDED' WHERE id=?",id);
        else refund(id,DELIVERY.plusSeconds(2),1);
        var m=revenue.dashboard(null,null).month().metrics();
        assertThat(m.refunds().complete()).isFalse(); assertThat(m.refunds().value()).isNull();
        assertThat(m.netVendorSales().value()).isNull();
    }
    @Test void shippingOnlyCollectionAndRefundHaveKnownZeroMerchandise() {
        long id=order(shop,DELIVERY,DELIVERY);
        jdbc.update("UPDATE uteexpress.orders SET discount_total=100000,grand_total=15000,commission_amount=0 WHERE id=?",id);
        jdbc.update("UPDATE uteexpress.payments SET amount=15000 WHERE order_id=?",id);
        receipt(id,DELIVERY.plusSeconds(1)); refund(id,DELIVERY.plusSeconds(2));
        var m=revenue.dashboard(null,null).month().metrics();
        money(m.deliveredSales(),"0"); money(m.collectedPayments(),"0"); money(m.refunds(),"0"); money(m.netCommission(),"0");
    }
    @Test void inconsistentRefundAndMissingReceiptHistoryExposeUnavailableAdjustments() {
        long id=order(shop,DELIVERY,DELIVERY); receipt(id,DELIVERY.plusSeconds(1));
        jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=? AND to_status='RETURNED'",id);
        refund(id,DELIVERY.plusSeconds(2));
        var result=revenue.dashboard(null,null);
        assertThat(result.month().metrics().refunds().value()).isNull();
        assertThat(result.month().metrics().netCommission().value()).isNull();
        assertThat(result.topProducts().complete()).isFalse();
    }
    @Test void unpaidNewOrdersCountWithoutInventingRevenueAndCancellationUsesOwnTime() {
        long id=order(shop,null,null);
        jdbc.update("UPDATE uteexpress.orders SET status='CANCELLED',cancelled_at=?,created_at=? WHERE id=?",ts(DELIVERY),ts(Instant.parse("2026-12-01T00:00:00Z")),id);
        var m=revenue.dashboard(null,null).month().metrics();
        money(m.deliveredSales(),"0"); money(m.collectedPayments(),"0");
        assertThat(m.totalOrders()).isZero(); assertThat(m.deliveredOrders()).isZero(); assertThat(m.cancelledOrders()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"2026-12-31T16:59:59.999999Z","2026-12-31T17:00:00Z","2027-01-01T16:59:59.999999Z","2027-01-01T17:00:00Z",
            "2027-01-31T16:59:59.999999Z","2027-01-31T17:00:00Z"})
    void exactLocalDayYearAndUpperBoundsAreHalfOpen(String value) {
        Instant at=Instant.parse(value); order(shop,at,at);
        var result=revenue.dashboard(null,null);
        boolean inDay=!at.isBefore(Instant.parse("2026-12-31T17:00:00Z")) && at.isBefore(Instant.parse("2027-01-01T17:00:00Z"));
        money(result.today().metrics().deliveredSales(),inDay ? "90000":"0");
        assertThat(result.today().metrics().deliveredOrders()).isEqualTo(inDay ? 1:0);
        boolean inMonth=!at.isBefore(Instant.parse("2026-12-31T17:00:00Z")) && at.isBefore(Instant.parse("2027-01-31T17:00:00Z"));
        money(result.month().metrics().deliveredSales(),inMonth ? "90000":"0");
    }
    @Test void stableProductIdTieBreakAndNegativeReturnRankArePreserved() {
        long id=order(shop,DELIVERY,DELIVERY); jdbc.update("UPDATE uteexpress.order_items SET quantity=1,line_total=final_unit_price WHERE order_id=?",id);
        var top=revenue.dashboard(null,null).topProducts().products();
        assertThat(top).extracting(RevenueSummary.Product::productId).containsExactly(product,secondProduct);
        receipt(id,Instant.parse("2027-02-01T00:00:00Z"));
        var negative=revenue.dashboard(LocalDate.of(2027,2,1),LocalDate.of(2027,3,1)).topProducts().products();
        assertThat(negative).extracting(RevenueSummary.Product::productId).containsExactly(product,secondProduct);
        assertThat(negative).allSatisfy(p -> assertThat(p.netQuantity()).isEqualTo(-1));
    }
    @ParameterizedTest @ValueSource(strings={"role","account","shop"})
    void stalePrincipalCannotOverrideCurrentAuthorization(String restriction) {
        switch(restriction) {
            case "role" -> jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",vendor);
            case "account" -> jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",vendor);
            case "shop" -> jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='TEST' WHERE id=?",shop);
        }
        assertThatThrownBy(() -> revenue.dashboard(null,null)).isInstanceOfSatisfying(ApplicationException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
    @Test void htmlJsonRolesAndTamperedFiltersAreProtectedAndEscaped() throws Exception {
        order(shop,DELIVERY,DELIVERY);
        mvc.perform(get("/vendor/dashboard").with(user(principal(vendor,"VENDOR"))).accept("text/html"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Snapshot &lt;script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Net Vendor Sales")));
        mvc.perform(get("/vendor/dashboard").with(user(principal(vendor,"VENDOR"))).accept("application/json"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.month.metrics.netVendorSales.value").value(90000))
            .andExpect(jsonPath("$.month.metrics.deliveredSales.complete").value(true));
        for(String field:List.of("shopId","amount","ownerId")) mvc.perform(get("/vendor/dashboard").param(field,Long.toString(otherShop))
            .with(user(principal(vendor,"VENDOR"))).accept("application/json")).andExpect(status().isBadRequest());
        for(String role:List.of("USER","MANAGER","ADMIN","SHIPPER")) mvc.perform(get("/vendor/dashboard")
            .with(user(principal(vendor,role))).accept("application/json")).andExpect(status().isForbidden());
        mvc.perform(get("/vendor/dashboard").with(anonymous()).accept("application/json")).andExpect(status().isUnauthorized());
        mvc.perform(get("/vendor/dashboard").param("from","bad").with(user(principal(vendor,"VENDOR"))).accept("text/html"))
            .andExpect(status().isBadRequest()).andExpect(view().name("order/error"));
    }
    @Test void snapshotSurvivesConcurrentFinancialChangeAndRevocationButNextRequestIsDenied() throws Exception {
        long id=order(shop,DELIVERY,DELIVERY); var calls=new AtomicInteger(); var pool=Executors.newSingleThreadExecutor();
        var target=(RevenueQueryRepository)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(repository);
        doAnswer(call -> {
            assertThat(jdbc.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("repeatable read");
            assertThat(jdbc.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");
            Object result=call.callRealMethod();
            if(calls.incrementAndGet()==1) pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                jdbc.update("UPDATE uteexpress.payments SET amount=2 WHERE order_id=?",id);
                jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",vendor);
            })).get(10,TimeUnit.SECONDS);
            return result;
        }).when(target).metrics(anyLong(),any());
        try {
            var result=revenue.dashboard(LocalDate.of(2027,1,1),LocalDate.of(2027,1,3));
            money(result.today().metrics().deliveredSales(),"90000"); money(result.month().metrics().deliveredSales(),"90000");
            money(result.selected().metrics().collectedPayments(),"90000");
            assertThatThrownBy(() -> revenue.dashboard(null,null)).isInstanceOf(ApplicationException.class);
        } finally { pool.shutdownNow(); }
    }
    @Test void unavailableTotalsAreExplicitInHtmlAndJson() throws Exception {
        long id=order(shop,DELIVERY,DELIVERY);
        jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=?",id);
        mvc.perform(get("/vendor/dashboard").with(user(principal(vendor,"VENDOR"))).accept("text/html"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa đủ dữ liệu")));
        mvc.perform(get("/vendor/dashboard").with(user(principal(vendor,"VENDOR"))).accept("application/json"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.month.metrics.deliveredSales.complete").value(false))
            .andExpect(jsonPath("$.month.metrics.deliveredSales.value").isEmpty())
            .andExpect(jsonPath("$.month.metrics.deliveredSales.unknownEvents").value(1));
    }
    @Test void analyticalStatementCountDoesNotGrowWithItemCount() {
        long id=order(shop,DELIVERY,DELIVERY);
        for(int count:List.of(2,12)) {
            if(count>2) for(int i=2;i<count;i++) item(id,product,1,1);
            clearInvocations(named);
            revenue.dashboard(LocalDate.of(2027,1,1),LocalDate.of(2027,1,3));
            verify(named,times(3)).queryForObject(eq(RevenueQueryRepository.METRICS_SQL),any(SqlParameterSource.class),any(RowMapper.class));
            verify(named,times(1)).query(eq(RevenueQueryRepository.TOP_SQL),any(SqlParameterSource.class),any(ResultSetExtractor.class));
        }
    }
    @Test void outerTransactionIsRejectedAndRolledBackEvidenceIsNotReported() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            order(shop,DELIVERY,DELIVERY);
            assertThatThrownBy(() -> revenue.dashboard(null,null)).isInstanceOf(ApplicationException.class); tx.setRollbackOnly();
        });
        money(revenue.dashboard(null,null).month().metrics().deliveredSales(),"0");
    }
    @Test void explainActualQueriesOnRepresentativeSparseAndDenseFixtures() throws Exception {
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
            .append("\nAdded representative fixture: 1200 Orders across 2 Shops, 3600 Items, 1200 Payments, 120 Receipts, 120 Refunds. No new indexes.\n")
            .append("Fixture scope: 600 Orders in the selected Shop. Other tests may also have rows in this disposable database.\n");
        for(String table:List.of("orders","payments","order_items","order_status_history","return_requests","refunds"))
            output.append("database rows ").append(table).append('=').append(jdbc.queryForObject("SELECT count(*) FROM uteexpress."+table,Long.class)).append('\n');
        for(var window:List.of(new RevenuePeriod(LocalDate.of(2026,1,1),LocalDate.of(2026,1,2)),
                new RevenuePeriod(LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)))) {
            var args=RevenueQueryRepository.parameters(shop,window);
            for(String sql:List.of(RevenueQueryRepository.METRICS_SQL,RevenueQueryRepository.TOP_SQL)) {
                named.queryForList(sql,args); // warm-up
                var plan=named.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) "+sql,args,String.class);
                output.append("\nquery=").append(sql.equals(RevenueQueryRepository.METRICS_SQL) ? "METRICS_SQL":"TOP_SQL")
                    .append(" shop=").append(shop).append(" start=").append(window.start()).append(" end=").append(window.end()).append('\n')
                    .append(String.join("\n",plan)).append('\n');
                assertThat(String.join("\n",plan)).contains("Execution Time","Buffers:");
            }
        }
        Files.writeString(Path.of("target/dash01-explain.txt"),output.toString());
        Files.writeString(Path.of("target/dash01-explain-queries.sql"),RevenueQueryRepository.METRICS_SQL+";\n"+RevenueQueryRepository.TOP_SQL+";\n");
        System.out.println("DASH-01 EXPLAIN evidence: target/dash01-explain.txt");
    }
    private RevenueSummary.Window period(String from,String to) { return revenue.dashboard(LocalDate.parse(from),LocalDate.parse(to)).selected(); }
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
        jdbc.update("""
            INSERT INTO uteexpress.refunds(order_id,payment_id,return_request_id,processed_by,operation,idempotency_key,schema_version,
                expected_order_version,expected_return_request_version,canonical_payload,payload_sha256,amount,status,proof_reference,
                server_timestamp,created_at,result_order_version,payment_paid_at,return_received_at)
            SELECT o.id,p.id,t.id,?,'PAY02_MANUAL_COD_REFUND_V1',?,1,0,0,decode('01','hex'),decode(repeat('00',32),'hex'),o.grand_total-?,
                'SUCCEEDED','PROOF',?,?,1,p.paid_at,t.received_at FROM uteexpress.orders o JOIN uteexpress.payments p ON p.order_id=o.id
                JOIN uteexpress.return_requests t ON t.order_id=o.id WHERE o.id=?
            """,vendor,UUID.randomUUID().toString(),amountDifference,ts(at),ts(at),id);
        jdbc.update("UPDATE uteexpress.orders SET status='REFUNDED' WHERE id=?",id);
    }
    private static Timestamp ts(Instant at) { return at==null ? null:Timestamp.from(at); }
    private static void money(RevenueAmount amount,String value) { assertThat(amount.complete()).isTrue(); assertThat(amount.value()).isEqualByComparingTo(value); }
    private static UteExpressPrincipal principal(long id,String role) { return new UteExpressPrincipal(id,"not-an-id",null,0L,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true); }
    private static void auth(long id) { var p=principal(id,"VENDOR"); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities())); }
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock revenueClock() { return Clock.fixed(Instant.parse("2027-01-01T01:30:00Z"),ZoneOffset.UTC); }
    }
}
