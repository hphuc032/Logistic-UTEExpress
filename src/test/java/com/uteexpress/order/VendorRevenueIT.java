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
        long a=order(shop,DELIVERY,DELIVERY.minusSeconds(1)); long b=order(shop,DELIVERY,DELIVERY.minusSeconds(1));
        unsuccessfulPayment(a,1,Instant.parse("2026-01-01T00:00:00Z"));
        unsuccessfulPayment(b,2,Instant.parse("2026-01-02T00:00:00Z"));
        unsuccessfulPayment(b,3,Instant.parse("2026-01-03T00:00:00Z"));
        order(otherShop,DELIVERY,DELIVERY.minusSeconds(1));
        jdbc.update("UPDATE uteexpress.orders SET status='RETURN_REQUESTED' WHERE id=?",a);
        var result=revenue.dashboard(null,null); var m=result.month().metrics();
        money(m.deliveredSales(),"180000"); money(m.collectedPayments(),"180000"); money(m.commissionAccrued(),"18000");
        money(m.netVendorSales(),"180000"); assertThat(m.deliveredOrders()).isEqualTo(2);
        money(m.netCollectedRevenue(),"180000"); money(m.netRevenueAfterCommission(),"162000");
        assertThat(result.topProducts().products()).extracting(RevenueSummary.Product::netQuantity).containsExactly(4L,2L);
    }
    @Test void independentCollectionDeliveryReceiptAndRefundPeriodsUseOriginalSnapshot() {
        Instant paid=Instant.parse("2026-12-31T16:59:59.999999Z");
        Instant delivered=Instant.parse("2026-12-31T17:00:00Z");
        long id=order(shop,delivered,null);
        poisonInitialUnpaidAttempt(id);
        successfulPayment(id,paid);
        unsuccessfulPayment(id,2,Instant.parse("2028-01-01T00:00:00Z"));
        receipt(id,Instant.parse("2027-02-01T00:00:00Z"));
        refund(id,Instant.parse("2027-03-01T00:00:00Z"));
        var december=period("2026-12-01","2027-01-01");
        money(december.metrics().collectedPayments(),"90000"); money(december.metrics().deliveredSales(),"0");
        money(december.metrics().netCollectedRevenue(),"90000"); money(december.metrics().netRevenueAfterCommission(),"90000");
        var january=period("2027-01-01","2027-02-01");
        money(january.metrics().deliveredSales(),"90000"); money(january.metrics().collectedPayments(),"0");
        money(january.metrics().commissionAccrued(),"9000");
        money(january.metrics().netCollectedRevenue(),"0"); money(january.metrics().netRevenueAfterCommission(),"-9000");
        var feb=revenue.dashboard(LocalDate.of(2027,2,1),LocalDate.of(2027,3,1));
        money(feb.selected().metrics().refunds(),"0");
        money(feb.selected().metrics().netCollectedRevenue(),"0"); money(feb.selected().metrics().netRevenueAfterCommission(),"0");
        assertThat(feb.topProducts().products()).extracting(RevenueSummary.Product::netQuantity).containsExactly(-1L,-2L);
        var march=revenue.dashboard(LocalDate.of(2027,3,1),LocalDate.of(2027,4,1));
        money(march.selected().metrics().refunds(),"90000"); money(march.selected().metrics().commissionReversed(),"9000");
        money(march.selected().metrics().netVendorSales(),"-90000"); money(march.selected().metrics().netCommission(),"-9000");
        money(march.selected().metrics().netCollectedRevenue(),"-90000"); money(march.selected().metrics().netRevenueAfterCommission(),"-81000");
        assertThat(march.topProducts().products()).isEmpty();
        money(period("2027-01-01","2027-02-01").metrics().deliveredSales(),"90000");
    }
    @Test void samePeriodFullRefundAndReceiptCancelOnlyTheirOwnMetrics() {
        long id=order(shop,DELIVERY,DELIVERY.minusSeconds(1)); receipt(id,DELIVERY.plusSeconds(1)); refund(id,DELIVERY.plusSeconds(2));
        var result=revenue.dashboard(null,null); var m=result.month().metrics();
        money(m.deliveredSales(),"90000"); money(m.collectedPayments(),"90000"); money(m.refunds(),"90000");
        money(m.netVendorSales(),"0"); money(m.netCommission(),"0");
        money(m.netCollectedRevenue(),"0"); money(m.netRevenueAfterCommission(),"0");
        assertThat(result.topProducts().products()).allSatisfy(p -> assertThat(p.netQuantity()).isZero());
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?",String.class,id)).isEqualTo("PAID");
    }
    @Test void nullPolicyAndLegitimateZeroRemainCompleteEvenWhenCurrentPolicyChanges() {
        long id=order(shop,DELIVERY,DELIVERY);
        jdbc.update("UPDATE uteexpress.orders SET commission_policy_id=NULL,commission_amount=0,commission_rate_snapshot=0 WHERE id=?",id);
        var first=revenue.dashboard(null,null); money(first.month().metrics().commissionAccrued(),"0");
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (99,'2027-01-01',?)",vendor);
        money(revenue.dashboard(null,null).month().metrics().commissionAccrued(),"0");
        money(revenue.dashboard(null,null).month().metrics().netRevenueAfterCommission(),"90000");
    }
    @ParameterizedTest @ValueSource(strings={"commission","paymentAmount","missingPayment","onlyUnpaid","missingPaidTime","deliveryHistory","deliveryTime","expired","online"})
    void invalidEvidenceDoesNotBecomeACompleteZero(String stage) {
        long id=order(shop,DELIVERY,DELIVERY);
        switch(stage) {
            case "commission" -> jdbc.update("UPDATE uteexpress.orders SET commission_amount=9000.50 WHERE id=?",id);
            case "paymentAmount" -> jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=?",id);
            case "missingPayment" -> jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?",id);
            case "onlyUnpaid" -> {
                jdbc.update("UPDATE uteexpress.payments SET status='UNPAID' WHERE order_id=?",id);
                unsuccessfulPayment(id,1,DELIVERY.minusSeconds(1));
            }
            case "missingPaidTime" -> jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=?",id);
            case "deliveryHistory" -> jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=?",id);
            case "deliveryTime" -> jdbc.update("UPDATE uteexpress.orders SET delivered_at=NULL WHERE id=?",id);
            case "expired" -> jdbc.update("UPDATE uteexpress.payments SET expired_at=paid_at WHERE order_id=?",id);
            case "online" -> jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?",id);
        }
        var m=revenue.dashboard(null,null).month().metrics();
        assertThat(m.commissionAccrued().complete()).isFalse(); assertThat(m.commissionAccrued().value()).isNull();
        if(stage.equals("commission")) money(m.deliveredSales(),"90000");
        else { assertThat(m.deliveredSales().value()).isNull(); assertThat(m.netVendorSales().value()).isNull(); }
        if(List.of("paymentAmount","missingPayment","onlyUnpaid","missingPaidTime","expired","online").contains(stage)) {
            assertThat(m.collectedPayments().complete()).isFalse(); assertThat(m.collectedPayments().value()).isNull();
            assertThat(m.netCollectedRevenue().complete()).isFalse(); assertThat(m.netCollectedRevenue().value()).isNull();
        } else money(m.netCollectedRevenue(),"90000");
        assertThat(m.netRevenueAfterCommission().complete()).isFalse(); assertThat(m.netRevenueAfterCommission().value()).isNull();
    }
    @ParameterizedTest @ValueSource(ints={1,3,7})
    void unsuccessfulAttemptsBeforeOnePaidRowAreIgnoredWithoutMixingEvidence(int unsuccessfulCount) {
        long id=order(shop,DELIVERY,null);
        poisonInitialUnpaidAttempt(id);
        for(int i=1;i<unsuccessfulCount;i++) unsuccessfulPayment(id,i+1,Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        Instant paidAt=DELIVERY.minusSeconds(1);
        long paid=successfulPayment(id,paidAt);
        var facts=named.queryForMap(RevenueQueryRepository.EVIDENCE_SQL+
            " SELECT payment_id,paid_amount,paid_at,paid_count,payment_ok FROM facts WHERE id=:orderId",
            new MapSqlParameterSource("shop",shop).addValue("orderId",id));
        assertThat(facts).containsEntry("payment_id",paid).containsEntry("paid_count",1L).containsEntry("payment_ok",true);
        assertThat((BigDecimal)facts.get("paid_amount")).isEqualByComparingTo("105000");
        assertThat(((Timestamp)facts.get("paid_at")).toInstant()).isEqualTo(paidAt);
        assertThat(jdbc.queryForObject("SELECT min(id) FROM uteexpress.payments WHERE order_id=?",Long.class,id)).isLessThan(paid);
        var result=revenue.dashboard(null,null); var m=result.month().metrics();
        money(m.deliveredSales(),"90000"); money(m.collectedPayments(),"90000"); money(m.commissionAccrued(),"9000");
        money(m.netVendorSales(),"90000"); money(m.netCommission(),"9000");
        money(m.netCollectedRevenue(),"90000"); money(m.netRevenueAfterCommission(),"81000");
        assertThat(result.topProducts().products()).extracting(RevenueSummary.Product::netQuantity).containsExactly(2L,1L);
        assertThat(period("2026-01-01","2026-02-01").metrics().collectedPayments().value()).isEqualByComparingTo("0");
    }
    @Test void extraUnpaidAttemptAfterSuccessfulPaymentIsNotAmbiguous() {
        long id=order(shop,DELIVERY,DELIVERY);
        unsuccessfulPayment(id,1,Instant.parse("2026-01-01T00:00:00Z"));
        var m=revenue.dashboard(null,null).month().metrics();
        money(m.deliveredSales(),"90000"); money(m.collectedPayments(),"90000"); money(m.commissionAccrued(),"9000");
        money(m.netVendorSales(),"90000");
    }
    @ParameterizedTest @ValueSource(strings={"COD","ONLINE"})
    void databaseRejectsSecondPaidRecordEvenWhenItContradictsSuccessfulEvidence(String method) {
        long id=order(shop,DELIVERY,DELIVERY);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at,expired_at)
            VALUES (?,?,'PAID',1,?,?,?)
            """,id,method,UUID.randomUUID().toString(),ts(DELIVERY.minusSeconds(100)),ts(DELIVERY)))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
            .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,e -> {
                assertThat(e.getSQLState()).isEqualTo("23505");
                assertThat(e.getMessage()).contains("uq_payments_order_id_paid");
            });
        String index=jdbc.queryForObject("SELECT pg_get_indexdef('uteexpress.uq_payments_order_id_paid'::regclass)",String.class);
        assertThat(index).contains("UNIQUE INDEX","order_id","WHERE","PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.payments WHERE order_id=? AND status='PAID'",Long.class,id)).isEqualTo(1);
        money(revenue.dashboard(null,null).month().metrics().deliveredSales(),"90000");
        // The production unique index is kept intact. paid_count=1 also gates the
        // production join; no constraint-bypassing fixture fabricates a second row.
    }
    @Test void refundUsesExactSuccessfulIdAmongEarlierAndLaterUnsuccessfulAttempts() {
        long id=order(shop,DELIVERY,null); poisonInitialUnpaidAttempt(id);
        long paid=successfulPayment(id,DELIVERY);
        unsuccessfulPayment(id,2,Instant.parse("2026-01-01T00:00:00Z"));
        receipt(id,DELIVERY.plusSeconds(1)); refund(id,DELIVERY.plusSeconds(2));
        assertThat(jdbc.queryForObject("SELECT payment_id FROM uteexpress.refunds WHERE order_id=?",Long.class,id)).isEqualTo(paid);
        var m=revenue.dashboard(null,null).month().metrics();
        money(m.deliveredSales(),"90000"); money(m.collectedPayments(),"90000"); money(m.refunds(),"90000");
        money(m.commissionAccrued(),"9000"); money(m.commissionReversed(),"9000");
        money(m.netVendorSales(),"0"); money(m.netCommission(),"0");
    }
    @ParameterizedTest @ValueSource(strings={"unpaidSameOrder","paidOtherOrder"})
    void refundCannotAttributeAnUnsuccessfulOrForeignPayment(String source) {
        long id=order(shop,DELIVERY,DELIVERY); receipt(id,DELIVERY.plusSeconds(1));
        if(source.equals("unpaidSameOrder")) {
            // Same amount/time as the success: only the payment ID/status differs.
            // The composite FK allows this same-Order row, so the read must reject it.
            long unpaid=jdbc.queryForObject("""
                INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at)
                VALUES (?,'COD','UNPAID',105000,?,?) RETURNING id
                """,Long.class,id,UUID.randomUUID().toString(),ts(DELIVERY));
            refundUsingPayment(id,DELIVERY.plusSeconds(2),0,unpaid);
            var m=revenue.dashboard(null,null).month().metrics();
            money(m.deliveredSales(),"90000"); money(m.collectedPayments(),"90000");
            assertThat(m.refunds().value()).isNull(); assertThat(m.commissionReversed().value()).isNull();
            assertThat(m.netVendorSales().complete()).isFalse(); assertThat(m.netCommission().complete()).isFalse();
        } else {
            long other=order(otherShop,DELIVERY,DELIVERY);
            long foreign=jdbc.queryForObject("SELECT id FROM uteexpress.payments WHERE order_id=? AND status='PAID'",Long.class,other);
            assertThatThrownBy(() -> refundUsingPayment(id,DELIVERY.plusSeconds(2),0,foreign))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,e -> {
                    assertThat(e.getSQLState()).isEqualTo("23503"); assertThat(e.getMessage()).contains("fk_refunds_payment_order");
                });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.refunds WHERE order_id=?",Long.class,id)).isZero();
        }
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Net Vendor Sales")))
            .andExpect(result -> assertThat(metricCells(result,"Net Collected Revenue")).containsExactly("90.000","90.000","90.000"))
            .andExpect(result -> assertThat(metricCells(result,"Net Revenue After Commission")).containsExactly("81.000","81.000","81.000"));
        var json=mvc.perform(get("/vendor/dashboard").with(user(principal(vendor,"VENDOR"))).accept("application/json"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.month.metrics.netVendorSales.value").value(90000))
            .andExpect(jsonPath("$.month.metrics.deliveredSales.complete").value(true));
        for(String window:List.of("today","month","selected")) {
            json.andExpect(jsonPath("$."+window+".metrics.netCollectedRevenue.value").value(90000))
                .andExpect(jsonPath("$."+window+".metrics.netCollectedRevenue.complete").value(true))
                .andExpect(jsonPath("$."+window+".metrics.netCollectedRevenue.unknownEvents").value(0))
                .andExpect(jsonPath("$."+window+".metrics.netRevenueAfterCommission.value").value(81000))
                .andExpect(jsonPath("$."+window+".metrics.netRevenueAfterCommission.complete").value(true))
                .andExpect(jsonPath("$."+window+".metrics.netRevenueAfterCommission.unknownEvents").value(0));
        }
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
    @Test void signedCashAndCommissionDeltasAreVisibleInCustomPeriodJsonAndHtml() throws Exception {
        long id=order(shop,DELIVERY,Instant.parse("2026-12-31T16:59:59.999999Z"));
        receipt(id,Instant.parse("2027-02-01T00:00:00Z")); refund(id,Instant.parse("2027-03-01T00:00:00Z"));
        mvc.perform(get("/vendor/dashboard").param("from","2027-03-01").param("toExclusive","2027-04-01")
                .with(user(principal(vendor,"VENDOR"))).accept("application/json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month.metrics.netVendorSales.value").value(90000))
            .andExpect(jsonPath("$.month.metrics.netCollectedRevenue.value").value(0))
            .andExpect(jsonPath("$.month.metrics.netRevenueAfterCommission.value").value(-9000))
            .andExpect(jsonPath("$.selected.metrics.netCollectedRevenue.value").value(-90000))
            .andExpect(jsonPath("$.selected.metrics.netCommission.value").value(-9000))
            .andExpect(jsonPath("$.selected.metrics.netRevenueAfterCommission.value").value(-81000))
            .andExpect(jsonPath("$.selected.metrics.netRevenueAfterCommission.complete").value(true));
        mvc.perform(get("/vendor/dashboard").param("from","2027-03-01").param("toExclusive","2027-04-01")
                .with(user(principal(vendor,"VENDOR"))).accept("text/html"))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(metricCells(result,"Net Collected Revenue")).containsExactly("0","0","-90.000"))
            .andExpect(result -> assertThat(metricCells(result,"Net Revenue After Commission")).containsExactly("-9.000","-9.000","-81.000"));
    }
    @ParameterizedTest @ValueSource(strings={"payment","commission","refund"})
    void unavailableTotalsAreExplicitInHtmlAndJson(String evidence) throws Exception {
        long id=order(shop,DELIVERY,DELIVERY);
        switch(evidence) {
            case "payment" -> jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=?",id);
            case "commission" -> jdbc.update("UPDATE uteexpress.orders SET commission_amount=9000.50 WHERE id=?",id);
            case "refund" -> { receipt(id,DELIVERY.plusSeconds(1)); refund(id,DELIVERY.plusSeconds(2),1); }
        }
        var html=mvc.perform(get("/vendor/dashboard").with(user(principal(vendor,"VENDOR"))).accept("text/html"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa đủ dữ liệu")))
            .andExpect(result -> assertThat(metricCells(result,"Net Revenue After Commission"))
                .allSatisfy(value -> assertThat(value).startsWith("Chưa đủ dữ liệu")));
        var json=mvc.perform(get("/vendor/dashboard").with(user(principal(vendor,"VENDOR"))).accept("application/json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month.metrics.netRevenueAfterCommission.complete").value(false))
            .andExpect(jsonPath("$.month.metrics.netRevenueAfterCommission.value").isEmpty())
            .andExpect(jsonPath("$.month.metrics.netRevenueAfterCommission.unknownEvents").value(evidence.equals("commission") ? 1:2));
        if(evidence.equals("commission")) {
            json.andExpect(jsonPath("$.month.metrics.netCollectedRevenue.complete").value(true))
                .andExpect(jsonPath("$.month.metrics.netCollectedRevenue.value").value(90000));
            html.andExpect(result -> assertThat(metricCells(result,"Net Collected Revenue")).containsExactly("90.000","90.000","90.000"));
        } else {
            json.andExpect(jsonPath("$.month.metrics.netCollectedRevenue.complete").value(false))
                .andExpect(jsonPath("$.month.metrics.netCollectedRevenue.value").isEmpty());
            html.andExpect(result -> assertThat(metricCells(result,"Net Collected Revenue"))
                .allSatisfy(value -> assertThat(value).startsWith("Chưa đủ dữ liệu")));
        }
        if(evidence.equals("payment")) json.andExpect(jsonPath("$.month.metrics.deliveredSales.complete").value(false))
            .andExpect(jsonPath("$.month.metrics.deliveredSales.value").isEmpty())
            .andExpect(jsonPath("$.month.metrics.deliveredSales.unknownEvents").value(1));
    }
    @Test void analyticalStatementCountDoesNotGrowWithItemCount() {
        long id=order(shop,DELIVERY,DELIVERY);
        for(int count:List.of(2,12)) {
            if(count>2) for(int i=2;i<count;i++) item(id,product,1,1);
            clearInvocations(named);
            var result=revenue.dashboard(LocalDate.of(2027,1,1),LocalDate.of(2027,1,3));
            for(var window:List.of(result.today(),result.month(),result.selected())) {
                money(window.metrics().netCollectedRevenue(),"90000"); money(window.metrics().netRevenueAfterCommission(),"81000");
            }
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
            """,vendor,UUID.randomUUID().toString(),amountDifference,ts(at),ts(at),paymentId,id);
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
    private static List<String> metricCells(org.springframework.test.web.servlet.MvcResult result,String label) throws Exception {
        String html=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var row=java.util.regex.Pattern.compile("(?s)<tr\\b[^>]*>\\s*<th\\b[^>]*>"+
            java.util.regex.Pattern.quote(label)+"[^<]*</th>(.*?)</tr>").matcher(html);
        assertThat(row.find()).as("Rendered metric row: %s",label).isTrue();
        var cells=java.util.regex.Pattern.compile("(?s)<td\\b[^>]*>(.*?)</td>").matcher(row.group(1));
        var values=new ArrayList<String>();
        while(cells.find()) values.add(cells.group(1).replaceAll("<[^>]*>","").trim());
        assertThat(values).hasSize(3);
        assertThat(row.find()).as("Metric has one row: %s",label).isFalse();
        return values;
    }
    private static void money(RevenueAmount amount,String value) { assertThat(amount.complete()).isTrue(); assertThat(amount.value()).isEqualByComparingTo(value); }
    private static UteExpressPrincipal principal(long id,String role) { return new UteExpressPrincipal(id,"not-an-id",null,0L,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true); }
    private static void auth(long id) { var p=principal(id,"VENDOR"); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities())); }
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock revenueClock() { return Clock.fixed(Instant.parse("2027-01-01T01:30:00Z"),ZoneOffset.UTC); }
    }
}
