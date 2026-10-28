package com.uteexpress.order;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.common.exception.*;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.service.*;
import com.uteexpress.payment.dto.*;
import com.uteexpress.payment.service.CancelledPaidRefundPayments;
import com.uteexpress.reporting.dto.*;
import com.uteexpress.reporting.repository.RevenueQueryRepository;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real checkout/cancellation; only the pre-existing PAID evidence is a clearly synthetic test fixture. */
@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(CancelledPaidRefundIT.Configuration.class) @Timeout(60)
class CancelledPaidRefundIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6");
    static final Instant AT=Instant.parse("2026-10-28T09:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService carts;
    @Autowired OrderPlacementService placement;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired CancelledPaidRefundService refunds;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired Events events;
    @Autowired RevenueQueryRepository revenue;
    @Autowired com.uteexpress.security.jwt.JwtTokenService tokens;
    @Autowired com.uteexpress.governance.service.RoleGovernanceService roles;
    @Autowired com.uteexpress.governance.service.AccountGovernanceService governance;
    @MockitoSpyBean RefundOrderAuthority authority;
    RefundOrderAuthority authoritySpy;
    TransactionTemplate tx;
    @MockitoSpyBean CancelledPaidRefundPayments payments;
    @MockitoSpyBean AuditLogService audit;
    CancelledPaidRefundPayments paymentSpy;
    AuditLogService auditSpy;
    long admin,otherAdmin,buyer,vendor,shop,product,address,provider,order;
    String key;
    @BeforeEach void fixture() {
        tx=new TransactionTemplate(transactions);authoritySpy=AopTestUtils.getUltimateTargetObject(authority);
        paymentSpy=AopTestUtils.getUltimateTargetObject(payments);auditSpy=AopTestUtils.getUltimateTargetObject(audit);
        admin=account("ADMIN");otherAdmin=account("ADMIN");buyer=account("USER");vendor=account("VENDOR");
        shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'QA02 refund',?,'Synthetic','APPROVED') RETURNING id",Long.class,vendor,UUID.randomUUID().toString());
        long category=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('QA02',?,true) RETURNING id",Long.class,UUID.randomUUID().toString());
        product=jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'QA02 product',100000,10,'ACTIVE') RETURNING id",Long.class,shop,category);
        address=jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Synthetic','0900000000','VN','District','Address',true) RETURNING id",Long.class,buyer);
        provider=jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'QA02',true) RETURNING id",Long.class,"Q02"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',15000,true)",provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7,?,?) ON CONFLICT (effective_from) DO NOTHING",ts(AT.minusSeconds(86400)),admin);
        order=cancelledPaidOrder();key=UUID.randomUUID().toString();auth(admin,"ADMIN");events.clear();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void successKeepsCancelledPaymentAndCancellationEffectsAndReplayFreezesResult() {
        var before=unchanged();var result=refunds.complete(command());
        assertThat(result.refundAmount()).isEqualByComparingTo("205000");assertThat(result.status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(result.proofReference()).isEqualTo("QA02-PROOF");assertThat(result.replay()).isFalse();
        assertThat(unchanged()).isEqualTo(before);assertRefundOnce();var committed=financialEffects();
        var replay=refunds.complete(command());assertThat(replay).isEqualTo(new RefundResult(result.refundId(),order,
                result.refundAmount(),result.status(),result.proofReference(),result.serverTimestamp(),true));
        assertThat(financialEffects()).usingRecursiveComparison().isEqualTo(committed);assertThat(events.financial).hasSize(1);
        assertThat(events.lifecycle).isEmpty();assertThat(unchanged()).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT return_request_id,expected_return_request_version,return_received_at,result_order_version FROM uteexpress.refunds WHERE order_id=?",order))
                .containsEntry("return_request_id",null).containsEntry("expected_return_request_version",null)
                .containsEntry("return_received_at",null).containsEntry("result_order_version",1L);
    }
    @ParameterizedTest @ValueSource(strings={"missing","unpaid","expired","paidAt","late","amountLow","amountHigh","zero","fraction","reference","delivered","history","release","status","createdAt","orderFraction"})
    void invalidPersistedEvidenceRejectsWithoutEffects(String kind) {
        switch(kind) {
            case "missing"->jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?",order);
            case "unpaid"->jdbc.update("UPDATE uteexpress.payments SET status='UNPAID',paid_at=NULL WHERE order_id=?",order);
            case "expired"->jdbc.update("UPDATE uteexpress.payments SET expired_at=paid_at WHERE order_id=?",order);
            case "paidAt"->jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=?",order);
            case "late"->jdbc.update("UPDATE uteexpress.payments SET paid_at=? WHERE order_id=?",ts(AT.plusSeconds(1)),order);
            case "amountLow"->jdbc.update("UPDATE uteexpress.payments SET amount=204999 WHERE order_id=?",order);
            case "amountHigh"->jdbc.update("UPDATE uteexpress.payments SET amount=205001 WHERE order_id=?",order);
            case "zero"->jdbc.update("UPDATE uteexpress.payments SET amount=0 WHERE order_id=?",order);
            case "fraction"->jdbc.update("UPDATE uteexpress.payments SET amount=205000.01 WHERE order_id=?",order);
            case "reference"->jdbc.update("UPDATE uteexpress.payments SET provider_reference=NULL WHERE order_id=?",order);
            case "delivered"->jdbc.update("UPDATE uteexpress.orders SET delivered_at=? WHERE id=?",ts(AT),order);
            case "history"->jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=? AND to_status='CANCELLED'",order);
            case "release"->jdbc.update("UPDATE uteexpress.orders SET inventory_released_at=NULL WHERE id=?",order);
            case "status"->jdbc.update("UPDATE uteexpress.orders SET status='NEW' WHERE id=?",order);
            case "createdAt"->jdbc.update("UPDATE uteexpress.payments SET created_at=? WHERE order_id=?",ts(AT.plusSeconds(1)),order);
            case "orderFraction"->jdbc.update("UPDATE uteexpress.orders SET subtotal=subtotal+0.01,grand_total=grand_total+0.01 WHERE id=?",order);
        }
        var before=unchanged();rejects(()->refunds.complete(command()),ErrorCode.CONFLICT);
        assertThat(unchanged()).isEqualTo(before);assertNoRefund();
    }
    @Test void databaseRejectsInconsistentOrderTotalsBeforeRefundCanUseThem() {
        var before=unchanged();
        assertThatThrownBy(()->jdbc.update("UPDATE uteexpress.orders SET subtotal=subtotal+1 WHERE id=?",order))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("ck_orders_money");
        assertThat(unchanged()).isEqualTo(before);assertNoRefund();
    }
    @Test void priorDeliveryHistoryCannotHideBehindAClearedDeliveryMarker() {
        jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id,created_at) VALUES (?,'SHIPPING','DELIVERED',?,?)",order,vendor,ts(AT.minusSeconds(1)));
        rejects(()->refunds.complete(command()),ErrorCode.CONFLICT);assertNoRefund();
    }
    @Test void unsuccessfulAttemptsDoNotMultiplyCollectionOrPreventTheSingleValidPayment() {
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,expired_at) VALUES (?,'ONLINE','UNPAID',205000,?,?)",order,UUID.randomUUID().toString(),ts(AT));
        refunds.complete(command());assertRefundOnce();assertRevenue("190000","190000","0");
    }
    @ParameterizedTest @ValueSource(strings={"actor","proof","version","key","order"})
    void replayCollisionsCannotCreateAnotherRefund(String kind) {
        refunds.complete(command());var before=financialEffects();var c=command();
        if(kind.equals("actor"))auth(otherAdmin,"ADMIN");
        if(kind.equals("proof"))c=new CancelledPaidRefundCommand(order,1L,"OTHER",key);
        if(kind.equals("version"))c=new CancelledPaidRefundCommand(order,0L,"QA02-PROOF",key);
        if(kind.equals("key"))c=new CancelledPaidRefundCommand(order,1L,"QA02-PROOF",UUID.randomUUID().toString());
        if(kind.equals("order")){long other=cancelledPaidOrder();auth(admin,"ADMIN");c=new CancelledPaidRefundCommand(other,1L,"QA02-PROOF",key);events.clear();}
        var request=c;rejects(()->refunds.complete(request),ErrorCode.CONFLICT);
        assertThat(financialEffects()).usingRecursiveComparison().isEqualTo(before);assertRefundOnce();
    }
    @Test void staleFirstAttemptAndOuterTransactionRejectWithoutReservingAKey() {
        rejects(()->refunds.complete(new CancelledPaidRefundCommand(order,0L,"QA02-PROOF",key)),ErrorCode.CONFLICT);
        new TransactionTemplate(transactions).executeWithoutResult(tx->rejects(()->refunds.complete(command()),ErrorCode.INVALID_REQUEST));
        assertNoRefund();assertThat(refunds.complete(command()).replay()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"USER","VENDOR","MANAGER","SHIPPER"})
    void nonAdminHttpActorsCannotRefundEvenAnOwnedOrder(String role) throws Exception {
        mvc.perform(post(url()).with(user(principal(buyer,role))).with(csrf()).header("Idempotency-Key",key)
                .contentType("application/json").content(body())).andExpect(status().isForbidden());assertNoRefund();
    }
    @Test @org.springframework.test.annotation.DirtiesContext(methodMode=org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    void realJwtAdminAndRenderedCookieCsrfSucceed() throws Exception {
        var auth=new jakarta.servlet.http.Cookie("UTEEXPRESS_AUTH",tokens.issue(principal(admin,"ADMIN")));
        var page=mvc.perform(get("/").cookie(auth).accept("text/html")).andExpect(status().isOk()).andReturn();
        var csrf=(org.springframework.security.web.csrf.CsrfToken)page.getRequest().getAttribute("_csrf");
        var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        mvc.perform(post(url()).cookie(auth,cookie).header(csrf.getHeaderName(),csrf.getToken()).header("Idempotency-Key",key)
                .contentType("application/json").content(body())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.refundAmount").value(205000));assertRefundOnce();
    }
    @Test void anonymousCsrfAndForgedAmountIdentityOrReturnFieldsCannotAuthorizeARefund() throws Exception {
        mvc.perform(post(url()).with(anonymous()).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mvc.perform(post(url()).with(user(principal(admin,"ADMIN"))).header("Idempotency-Key",key).contentType("application/json").content(body())).andExpect(status().isForbidden());
        for(String field:List.of("amount","refundAmount","actorId","paymentId","expectedReturnRequestVersion","status"))
            mvc.perform(post(url()).with(user(principal(admin,"ADMIN"))).with(csrf()).header("Idempotency-Key",key)
                    .contentType("application/json").content(body().replace("}",",\""+field+"\":999999}"))).andExpect(status().isBadRequest());
        mvc.perform(post(url()).with(user(principal(admin,"ADMIN"))).with(csrf()).header("Idempotency-Key",key,key)
                .contentType("application/json").content(body())).andExpect(status().isBadRequest());assertNoRefund();
    }
    @Test void missingKeyProofAndUnknownOrderFailSafely() throws Exception {
        mvc.perform(post(url()).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(body())).andExpect(status().isBadRequest());
        mvc.perform(post(url()).with(user(principal(admin,"ADMIN"))).with(csrf()).header("Idempotency-Key",key)
                .contentType("application/json").content("{\"expectedOrderVersion\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/orders/9223372036854775807/refunds/cancelled-paid").with(user(principal(admin,"ADMIN"))).with(csrf()).header("Idempotency-Key",key)
                .contentType("application/json").content(body())).andExpect(status().isNotFound());assertNoRefund();
    }
    @ParameterizedTest @ValueSource(strings={""," ","\u0000","\uD800"})
    void invalidProofCannotWriteEvidence(String proof) {
        rejects(()->refunds.complete(new CancelledPaidRefundCommand(order,1L,proof,key)),ErrorCode.INVALID_REQUEST);assertNoRefund();
    }
    @ParameterizedTest @ValueSource(strings={"role","account"})
    void currentPersistedAdminAuthorityIsRequiredOnFirstAttemptAndReplay(String kind) {
        refunds.complete(command());var before=financialEffects();
        if(kind.equals("role"))jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",admin);
        else jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",admin);
        assertThatThrownBy(()->refunds.complete(command())).isInstanceOf(ApplicationException.class);
        assertThat(financialEffects()).usingRecursiveComparison().isEqualTo(before);assertRefundOnce();
    }
    @ParameterizedTest @ValueSource(strings={"ledger","audit","beforeCommit"})
    void rollbackAtFlushedStagesRestoresEverythingAndAllowsRetry(String stage) {
        var before=unchanged();
        if(stage.equals("audit"))doAnswer(i->{i.callRealMethod();throw new InjectedFailure();}).when(auditSpy).append(any());
        else doAnswer(i->{
            Object result=i.callRealMethod();
            if(stage.equals("beforeCommit"))TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void beforeCommit(boolean readOnly){throw new InjectedFailure();}});
            else throw new InjectedFailure();return result;
        }).when(paymentSpy).record(any(),any(),anyLong(),any(),any());
        assertThatThrownBy(()->refunds.complete(command())).isInstanceOf(InjectedFailure.class);
        assertThat(unchanged()).isEqualTo(before);assertNoRefund();
        reset(paymentSpy,auditSpy);refunds.complete(command());assertRefundOnce();assertThat(events.financial).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"replay","differentKey","rollback","differentKeyRollback","actor","proof","order"})
    void contendersShowActualPostgresBlockersAndCommitOnce(String schedule) throws Exception {
        long target=schedule.equals("order")?cancelledPaidOrder():order;auth(admin,"ADMIN");events.clear();
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pids=new ConcurrentHashMap<String,Integer>();var once=new AtomicBoolean();
        doAnswer(i->{jdbc.execute("SET LOCAL lock_timeout='15s'");pids.put(Thread.currentThread().getName(),pid());return i.callRealMethod();}).when(paymentSpy).lockKey(any());
        doAnswer(i->{Object result=i.callRealMethod();if(Thread.currentThread().getName().equals("first")&&once.compareAndSet(false,true)){
            held.countDown();assertThat(release.await(12,TimeUnit.SECONDS)).isTrue();if(schedule.contains("Rollback")||schedule.equals("rollback"))throw new InjectedFailure();}return result;
        }).when(paymentSpy).record(any(),any(),anyLong(),any(),any());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->worker("first",admin,command()));assertThat(held.await(8,TimeUnit.SECONDS)).isTrue();assertNoRefund();
            var second=new CancelledPaidRefundCommand(target,1L,schedule.equals("proof")?"OTHER":"QA02-PROOF",schedule.startsWith("differentKey")?UUID.randomUUID().toString():key);
            var b=pool.submit(()->worker("second",schedule.equals("actor")?otherAdmin:admin,second));
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);boolean blocked=false;
            while(System.nanoTime()<end){Integer waiter=pids.get("second");if(waiter!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,pids.get("first"),waiter))){blocked=true;break;}java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));}
            assertThat(blocked).as("Actual holder/waiter %s",pids).isTrue();String waitingSql=jdbc.queryForObject("SELECT query FROM pg_stat_activity WHERE pid=?",String.class,pids.get("second"));assertThat(waitingSql).contains(schedule.startsWith("differentKey")?"orders":"pg_advisory_xact_lock");
            System.out.println("QA02 cancelled-paid schedule="+schedule+" pg_blocking_pids=verified SQL="+(schedule.startsWith("differentKey")?"orders":"advisory"));
            release.countDown();Object first=a.get(18,TimeUnit.SECONDS),secondResult=b.get(18,TimeUnit.SECONDS);
            if(schedule.contains("Rollback")||schedule.equals("rollback")){assertThat(first).isInstanceOf(InjectedFailure.class);assertThat(secondResult).isInstanceOfSatisfying(RefundResult.class,r->assertThat(r.replay()).isFalse());}
            else{assertThat(first).isInstanceOf(RefundResult.class);if(schedule.equals("replay"))assertThat(secondResult).isInstanceOfSatisfying(RefundResult.class,r->assertThat(r.replay()).isTrue());else assertThat(secondResult).isInstanceOfSatisfying(ApplicationException.class,e->assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));}
            assertRefundOnce();assertThat(events.financial).hasSize(1);assertThat(events.lifecycle).isEmpty();
        } finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void merchandiseCollectionAndRefundNetToZeroWithoutAccruingOrReversingCommission() {
        assertRevenue("190000","0","190000");var before=unchanged();refunds.complete(command());
        assertRevenue("190000","190000","0");assertThat(unchanged()).isEqualTo(before);
    }
    @Test void crossPeriodRefundRemainsSignedAndShippingExcluded() {
        Instant created=AT.minusSeconds(35*86400L),paid=AT.minusSeconds(32*86400L),cancel=AT.minusSeconds(10*86400L);
        jdbc.update("UPDATE uteexpress.orders SET created_at=?,cancelled_at=?,inventory_released_at=? WHERE id=?",ts(created),ts(cancel),ts(cancel),order);
        jdbc.update("UPDATE uteexpress.payments SET created_at=?,paid_at=? WHERE order_id=?",ts(created),ts(paid),order);
        jdbc.update("UPDATE uteexpress.order_status_history SET created_at=? WHERE order_id=? AND to_status='CANCELLED'",ts(cancel),order);
        refunds.complete(command());assertRevenue("0","190000","-190000");
    }
    @ParameterizedTest @ValueSource(strings={"createdAt","blankReference"})
    void invalidOnlineEvidenceCannotBecomeKnownCollectedRevenue(String kind) {
        if(kind.equals("createdAt"))jdbc.update("UPDATE uteexpress.payments SET created_at=? WHERE order_id=?",ts(AT.plusSeconds(1)),order);
        else jdbc.update("UPDATE uteexpress.payments SET provider_reference=? WHERE order_id=?","\t\n",order);
        rejects(()->refunds.complete(command()),ErrorCode.CONFLICT);assertNoRefund();
        var result=revenue.metrics(RevenueScope.shop(shop),new RevenuePeriod(LocalDate.of(2026,10,1),LocalDate.of(2026,11,1)));
        assertThat(result.collectedPayments().complete()).isFalse();
        assertThat(result.netCollectedRevenue().complete()).isFalse();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"refundFirst,role,false", "revocationFirst,role,false", "refundFirst,status,false", "revocationFirst,status,false",
            "refundFirst,role,true", "revocationFirst,role,true", "refundFirst,status,true", "revocationFirst,status,true"})
    void adminRevocationRacesRetainAuthorityThroughCommit(String schedule, String mutation, boolean replay) throws Exception {
        if (replay) refunds.complete(command());
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pids = new ConcurrentHashMap<String,Integer>();
        if (schedule.equals("refundFirst")) doAnswer(i -> {
            Object result = i.callRealMethod(); pids.put("refund", pid()); held.countDown(); await(release); return result;
        }).when(authoritySpy).authorizeAdmin();
        else doAnswer(i -> { pids.put("refund", pid()); return i.callRealMethod(); }).when(authoritySpy).authorizeAdmin();
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Object> refund = () -> worker("refund", admin, command());
            java.util.concurrent.Callable<Object> revoke = () -> {
                auth(otherAdmin, "ADMIN");
                try {
                    tx.executeWithoutResult(s -> {
                        pids.put("revoke", pid());
                        if (mutation.equals("role")) roles.change(admin, 0L, com.uteexpress.governance.dto.ManagedRole.ADMIN, false);
                        else governance.setLocked(admin, 0L, true);
                        if (schedule.equals("revocationFirst")) { held.countDown(); await(release); }
                    }); return "revoked";
                } finally { SecurityContextHolder.clearContext(); }
            };
            var a = pool.submit(schedule.equals("refundFirst") ? refund : revoke); await(held);
            var b = pool.submit(schedule.equals("refundFirst") ? revoke : refund);
            String waiter = schedule.equals("refundFirst") ? "revoke" : "refund";
            String holder = schedule.equals("refundFirst") ? "refund" : "revoke";
            awaitPid(pids, waiter); blocked(pids.get(waiter), pids.get(holder), "users"); release.countDown();
            Object firstResult = a.get(15, TimeUnit.SECONDS), secondResult = b.get(15, TimeUnit.SECONDS);
            Object refundResult = schedule.equals("refundFirst") ? firstResult : secondResult;
            if (schedule.equals("refundFirst")) { assertThat(refundResult).isInstanceOf(RefundResult.class); assertEffects(1); }
            else { assertThat(refundResult).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)); assertEffects(replay ? 1 : 0); }
            assertThat(events.financial).hasSize(replay || schedule.equals("refundFirst") ? 1 : 0);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
    }

    private static void await(CountDownLatch latch) { try { assertThat(latch.await(10,TimeUnit.SECONDS)).isTrue(); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); } }
    private static void awaitPid(Map<String,Integer> pids,String name) { long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(!pids.containsKey(name)&&System.nanoTime()<until)java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));assertThat(pids).containsKey(name); }
    private void blocked(int waiter,int holder,String query) { long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);boolean waiting=false;while(System.nanoTime()<until){if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,holder,waiter))){waiting=true;break;}java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));}assertThat(waiting).isTrue();assertThat(jdbc.queryForObject("SELECT query FROM pg_stat_activity WHERE pid=?",String.class,waiter)).contains(query);System.out.println("QA02 cancelled-paid ADMIN race pg_blocking_pids=verified"); }
    private void assertEffects(int expected) { if(expected==0)assertNoRefund();else assertRefundOnce(); }
    private void assertRevenue(String collected,String returned,String net) {
        var m=revenue.metrics(RevenueScope.shop(shop),new RevenuePeriod(LocalDate.of(2026,10,1),LocalDate.of(2026,11,1)));
        assertThat(m.collectedPayments().complete()).isTrue();assertThat(m.collectedPayments().value()).isEqualByComparingTo(collected);
        assertThat(m.refunds().complete()).isTrue();assertThat(m.refunds().value()).isEqualByComparingTo(returned);
        assertThat(m.netCollectedRevenue().value()).isEqualByComparingTo(net);assertThat(m.commissionAccrued().value()).isZero();assertThat(m.commissionReversed().value()).isZero();
        assertThat(m.deliveredSales().value()).isZero();assertThat(m.deliveredOrders()).isZero();assertThat(m.cancelledOrders()).isEqualTo(1);
    }
    private Map<String,Object> unchanged() {
        var result=new LinkedHashMap<String,Object>();for(String table:List.of("orders","payments","order_items","order_status_history","voucher_usages","notifications","return_requests","shipments"))
            result.put(table,jdbc.queryForList("SELECT * FROM uteexpress."+table+" WHERE "+(table.equals("orders")?"id":"order_id")+"=? ORDER BY id",order));
        result.put("stock",jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?",Integer.class,product));return result;
    }
    private Map<String,Object> financialEffects() {return Map.of("refund",jdbc.queryForList("SELECT * FROM uteexpress.refunds WHERE order_id=? ORDER BY id",order),"audit",jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE action='ORDER_CANCELLED_PAID_REFUNDED' AND target_id=? ORDER BY id",order));}
    private void assertNoRefund(){assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.refunds WHERE order_id=?",Integer.class,order)).isZero();assertThat(events.financial).isEmpty();}
    private void assertRefundOnce(){assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.refunds WHERE order_id=?",Integer.class,order)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE action='ORDER_CANCELLED_PAID_REFUNDED' AND target_id=?",Integer.class,order)).isEqualTo(1);assertThat(jdbc.queryForMap("SELECT status,version FROM uteexpress.orders WHERE id=?",order)).containsEntry("status","CANCELLED").containsEntry("version",1L);assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?",Integer.class,product)).isEqualTo(10);}
    private long cancelledPaidOrder() {
        String voucher="Q02"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by) VALUES (?,'SHOP','FIXED',?,10000,0,?,?,1,1,?)",voucher,shop,ts(AT.minusSeconds(86400)),ts(AT.plusSeconds(86400)),vendor);
        auth(buyer,"USER");carts.addProduct(new AddCartProductRequest(product,2));long id=placement.placeOrder(new CheckoutRequest(UUID.randomUUID().toString(),List.of(new CheckoutRequest.Item(product,2)),address,provider,"STANDARD",CheckoutRequest.PaymentMethod.COD,voucher)).orderId();
        // Synthetic persisted collection evidence only: never a VNPAY callback or production record.
        jdbc.update("UPDATE uteexpress.payments SET method='ONLINE',status='PAID',provider_reference=?,paid_at=?,updated_at=? WHERE order_id=?", "QA02_SYNTHETIC_"+UUID.randomUUID(),ts(AT),ts(AT),id);
        lifecycle.cancelForBuyer(new BuyerCancellationCommand(id,0L));return id;
    }
    private long account(String role){String name=UUID.randomUUID().toString().replace("-","").substring(0,20);long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test-only','ACTIVE') RETURNING id",Long.class,name+"@example.test",name+"@example.test",name,name);jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);return id;}
    private CancelledPaidRefundCommand command(){return new CancelledPaidRefundCommand(order,1L,"  QA02-PROOF  ",key);}
    private String url(){return "/admin/orders/"+order+"/refunds/cancelled-paid";}
    private String body(){return "{\"expectedOrderVersion\":1,\"proofReference\":\"QA02-PROOF\"}";}
    private int pid(){return jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class);}
    private Object worker(String name,long actor,CancelledPaidRefundCommand command){Thread.currentThread().setName(name);auth(actor,"ADMIN");try{return refunds.complete(command);}catch(RuntimeException e){return e;}finally{SecurityContextHolder.clearContext();}}
    private static java.sql.Timestamp ts(Instant at){return java.sql.Timestamp.from(at);}
    private static UteExpressPrincipal principal(long id,String role){return new UteExpressPrincipal(id,"opaque",null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true);}
    private static void auth(long id,String role){var p=principal(id,role);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    private static void rejects(Runnable call,ErrorCode code){assertThatThrownBy(call::run).isInstanceOfSatisfying(ApplicationException.class,e->assertThat(e.errorCode()).isEqualTo(code));}
    static class InjectedFailure extends RuntimeException { }
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock cancelledRefundClock(){return Clock.fixed(AT,ZoneOffset.UTC);}
        @Bean Events cancelledRefundEvents(){return new Events();}
    }
    static class Events {
        @Autowired javax.sql.DataSource dataSource;
        final List<CancelledPaidRefundRecordedEvent> financial=new CopyOnWriteArrayList<>();
        final List<OrderStatusChangedEvent> lifecycle=new CopyOnWriteArrayList<>();
        @EventListener void status(OrderStatusChangedEvent e){lifecycle.add(e);}
        @EventListener void financial(CancelledPaidRefundRecordedEvent e){
            try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT count(*) FROM uteexpress.refunds r JOIN uteexpress.orders o ON o.id=r.order_id WHERE r.id=? AND o.status='CANCELLED' AND EXISTS(SELECT 1 FROM uteexpress.audit_logs a WHERE a.target_id=o.id AND a.action='ORDER_CANCELLED_PAID_REFUNDED')")){
                s.setLong(1,e.refundId());try(var rs=s.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getInt(1)).isEqualTo(1);}
            }catch(java.sql.SQLException ex){throw new IllegalStateException(ex);}financial.add(e);
        }
        void clear(){financial.clear();lifecycle.clear();}
    }
}
