package com.uteexpress.order;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.common.exception.*;
import com.uteexpress.notification.dto.NotificationCommitted;
import com.uteexpress.notification.service.NotificationCommitPublisher;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.*;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.jwt.JwtTokenService;
import jakarta.servlet.http.Cookie;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.*;
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

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(BuyerCancellationIT.Recording.class) @Timeout(50)
class BuyerCancellationIT {
    @TestConfiguration(proxyBeanMethods=false) static class Recording {
        @Bean Signals cancellationSignals() { return new Signals(); }
    }
    static class Signals {
        final List<NotificationCommitted> hints=new CopyOnWriteArrayList<>();
        final List<OrderStatusChangedEvent> events=new CopyOnWriteArrayList<>();
        @EventListener void hint(NotificationCommitted hint) { hints.add(hint); }
        @EventListener void event(OrderStatusChangedEvent event) { events.add(event); }
        void clear() { hints.clear(); events.clear(); }
    }
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService carts;
    @Autowired OrderPlacementService placement;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Signals signals;
    @MockitoSpyBean BuyerCancellationAuthority authority;
    @MockitoSpyBean VendorOrderAuthority vendorAuthority;
    @MockitoSpyBean NotificationCommitPublisher publisher;
    @MockitoSpyBean SimpMessagingTemplate broker;
    BuyerCancellationAuthority guardSpy;
    long buyer, stranger, seller, product, secondProduct, order;

    @BeforeEach void fixture() {
        guardSpy=AopTestUtils.getUltimateTargetObject(authority);
        buyer=account("USER"); stranger=account("USER"); seller=account("VENDOR");
        long shop=shop(seller);
        long category=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Q02-F05',?,true) RETURNING id",Long.class,UUID.randomUUID().toString());
        product=product(shop,category,100000,10); secondProduct=product(shop,category,50000,20);
        long address=jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Buyer','0900000000','VN','District','Address',true) RETURNING id",Long.class,buyer);
        String suffix=UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
        long provider=jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Q02-F05',true) RETURNING id",Long.class,"Q05"+suffix.substring(0,16));
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',15000,true)",provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7,CURRENT_TIMESTAMP-INTERVAL '1 day',?)",seller);
        String voucher="Q05"+suffix;
        jdbc.update("INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by) VALUES (?,'SHOP','FIXED',?,10000,0,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day',1,1,?)",voucher,shop,seller);
        auth(buyer,"USER");
        carts.addProduct(new AddCartProductRequest(product,2)); carts.addProduct(new AddCartProductRequest(secondProduct,3));
        order=placement.placeOrder(new CheckoutRequest(UUID.randomUUID().toString(),
                List.of(new CheckoutRequest.Item(product,2),new CheckoutRequest.Item(secondProduct,3)),
                address,provider,"STANDARD",CheckoutRequest.PaymentMethod.COD,voucher)).orderId();
        assertThat(stocks()).containsExactly(8,17);
        assertThat(rows("payments").getFirst()).containsEntry("status","UNPAID");
        assertThat(rows("voucher_usages").getFirst()).containsEntry("status","REDEEMED");
        signals.clear(); clearInvocations(vendorAuthority,broker,publisher,guardSpy);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    // csrf() helpers replace the shared filter's repository; restore production cookie behavior first.
    @Test @DirtiesContext(methodMode=DirtiesContext.MethodMode.BEFORE_METHOD)
    void buyerPageSuppliesPersistedVersionAndRealMaskedCsrfForExistingEndpoint() throws Exception {
        var before=state();
        Cookie jwt=new Cookie("UTEEXPRESS_AUTH",tokens.issue(principal(buyer,"USER")));
        var page=mvc.perform(get("/orders/{id}",order).cookie(jwt).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-buyer-cancellation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cancel-url=\""+path()+"\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cancel-version=\"0\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/buyer-cancellation.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cancel-button disabled"))).andReturn();
        assertThat(state()).isEqualTo(before); noDispatch();
        CsrfToken csrf=(CsrfToken)page.getRequest().getAttribute("_csrf");
        Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(page.getResponse().getContentAsString()).contains("data-csrf-header=\""+csrf.getHeaderName()+"\"",
                "data-csrf-token=\""+csrf.getToken()+"\"");
        // Actual GET-issued cookie + rendered masked token; no test csrf() post-processor.
        mvc.perform(post(path()).cookie(jwt,cookie).header(csrf.getHeaderName(),csrf.getToken())
                .contentType("application/json").content(body(0))).andExpect(status().isOk());
        assertCancelledOnce();
        var committed=state();
        mvc.perform(post(path()).cookie(jwt,cookie).header(csrf.getHeaderName(),csrf.getToken())
                .contentType("application/json").content(body(0))).andExpect(status().isConflict());
        assertThat(state()).isEqualTo(committed);
        mvc.perform(get("/orders/{id}",order).cookie(jwt).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-buyer-cancellation"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CANCELLED")));
        // Public JSON contracts remain unchanged; action data belongs to the HTML model only.
        mvc.perform(get("/orders/{id}",order).cookie(jwt).accept("application/json"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancellationVersion").doesNotExist())
                .andExpect(jsonPath("$.expectedOrderVersion").doesNotExist());
    }

    @ParameterizedTest @ValueSource(strings={"CONFIRMED","PICKED_UP","SHIPPING","DELIVERED","CANCELLED","RETURN_REQUESTED","RETURNED","REFUNDED"})
    void nonNewBuyerPagesNeverRenderCancellationControl(String status) throws Exception {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?",status,order);
        var before=state();
        mvc.perform(get("/orders/{id}",order).with(user(principal(buyer,"USER"))).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-buyer-cancellation"))));
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @ParameterizedTest @ValueSource(strings={"principal","persistedRole","locked","released","cancelled","delivered"})
    void restrictedBuyerPagesNeverRenderCancellationControl(String restriction) throws Exception {
        String role=restriction.equals("principal")?"VENDOR":"USER";
        if(restriction.equals("persistedRole")) jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",buyer);
        if(restriction.equals("locked")) jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",buyer);
        if(restriction.equals("released")) jdbc.update("UPDATE uteexpress.orders SET inventory_released_at=CURRENT_TIMESTAMP WHERE id=?",order);
        if(restriction.equals("cancelled")) jdbc.update("UPDATE uteexpress.orders SET cancelled_at=CURRENT_TIMESTAMP WHERE id=?",order);
        if(restriction.equals("delivered")) jdbc.update("UPDATE uteexpress.orders SET delivered_at=CURRENT_TIMESTAMP WHERE id=?",order);
        var before=state();
        mvc.perform(get("/orders/{id}",order).with(user(principal(buyer,role))).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-buyer-cancellation"))));
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @Test void foreignOrMissingBuyerPagesCannotExposeActionOrVersion() throws Exception {
        for(long id:List.of(order,Long.MAX_VALUE))
            mvc.perform(get("/orders/{id}",id).with(user(principal(stranger,"USER"))).accept("text/html"))
                    .andExpect(status().isNotFound()).andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("data-buyer-cancellation"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-cancel-version"))));
        noDispatch();
    }

    @Test void pageVersionReflectsPersistedValueAndDoesNotAuthorizeStaleOrCsrfFreeCommand() throws Exception {
        jdbc.update("UPDATE uteexpress.orders SET version=7 WHERE id=?",order);
        mvc.perform(get("/orders/{id}",order).with(user(principal(buyer,"USER"))).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-cancel-version=\"7\"")));
        var before=state();
        request(body(0)).andExpect(status().isConflict());
        mvc.perform(post(path()).with(user(principal(buyer,"USER"))).contentType("application/json").content(body(7)))
                .andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before); noDispatch();
        request(body(7)).andExpect(status().isOk());
        assertThat(row()).containsEntry("status","CANCELLED").containsEntry("version",8L);
        assertThat(stocks()).containsExactly(10,20);
        assertThat(rows("voucher_usages").getFirst()).containsEntry("status","RELEASED");
    }

    @Test void realJwtUserCancelsOwnedNewOrderAndPreservesPaymentMoneyAndItemSnapshots() throws Exception {
        var money=immutableOrder(); var items=rows("order_items"); var payment=rows("payments");
        jdbc.update("UPDATE uteexpress.products SET price=999999,name='Changed catalog' WHERE id IN (?,?)",product,secondProduct);
        mvc.perform(post(path()).cookie(new Cookie("UTEEXPRESS_AUTH",tokens.issue(principal(buyer,"USER"))))
                .with(csrf()).contentType("application/json").accept("application/json").content(body(0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("OK"));
        assertCancelledOnce();
        assertThat(immutableOrder()).isEqualTo(money); assertThat(rows("order_items")).isEqualTo(items);
        assertThat(rows("payments")).isEqualTo(payment); assertThat(rows("refunds")).isEmpty();
        assertThat(row()).containsEntry("version",1L);
        assertThat(row().get("inventory_released_at")).isEqualTo(row().get("cancelled_at"));
        var history=rows("order_status_history").getLast();
        assertThat(history).containsEntry("from_status","NEW").containsEntry("to_status","CANCELLED").containsEntry("actor_id",buyer);
        assertThat(history.get("created_at")).isEqualTo(row().get("cancelled_at"));
        verifyNoInteractions(vendorAuthority);
        assertThat(signals.hints).hasSize(2); assertThat(signals.events).hasSize(1);
        verify(broker,times(2)).convertAndSendToUser(anyString(),eq("/queue/notifications"),any(Object.class));
    }

    @Test void foreignAndMissingOrdersReturnSame404WithoutChangingAnyEvidence() throws Exception {
        var before=state();
        for(long id:List.of(order,Long.MAX_VALUE))
            mvc.perform(post("/buyer/orders/{id}/cancel",id).with(user(principal(stranger,"USER"))).with(csrf())
                    .contentType("application/json").accept("application/json").content(body(0)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @ParameterizedTest @ValueSource(strings={"VENDOR","ADMIN","MANAGER","SHIPPER"})
    void nonUserRolesCannotCallBuyerEndpointEvenWhenTheyOwnTheOrder(String role) throws Exception {
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",buyer); grant(buyer,role);
        var before=state();
        mvc.perform(post(path()).with(user(principal(buyer,role))).with(csrf()).contentType("application/json")
                .accept("application/json").content(body(0))).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before); noDispatch(); verifyNoInteractions(guardSpy);
    }

    @Test void anonymousMissingAndInvalidCsrfAreBlocked() throws Exception {
        var before=state();
        mvc.perform(post(path()).with(anonymous()).with(csrf()).contentType("application/json")
                .accept("application/json").content(body(0))).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(user(principal(buyer,"USER"))).contentType("application/json")
                .accept("application/json").content(body(0))).andExpect(status().isForbidden());
        mvc.perform(post(path()).with(user(principal(buyer,"USER"))).with(csrf().useInvalidToken())
                .contentType("application/json").accept("application/json").content(body(0))).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @ParameterizedTest @ValueSource(strings={"{}","{\"expectedOrderVersion\":null}","{\"expectedOrderVersion\":-1}",
            "{\"expectedOrderVersion\":0,\"actorId\":1}","{\"expectedOrderVersion\":0,\"status\":\"CANCELLED\"}"})
    void versionBodyValidationRejectsMissingInvalidAndForgedFields(String body) throws Exception {
        var before=state();
        request(body).andExpect(status().isBadRequest());
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @Test void queryVersionCannotReplaceOrOverrideJsonBody() throws Exception {
        var before=state();
        mvc.perform(post(path()).param("expectedOrderVersion","0").with(user(principal(buyer,"USER"))).with(csrf())
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(path()).param("expectedOrderVersion","0").with(user(principal(buyer,"USER"))).with(csrf())
                .contentType("application/json").content(body(1))).andExpect(status().isBadRequest());
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @ParameterizedTest @ValueSource(longs={1,2,Long.MAX_VALUE})
    void staleVersionConflictsWithoutReleasingStockQuotaOrChangingPayment(long version) throws Exception {
        var before=state(); request(body(version)).andExpect(status().isConflict());
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @ParameterizedTest @ValueSource(strings={"CONFIRMED","PICKED_UP","SHIPPING","DELIVERED","CANCELLED","RETURN_REQUESTED","RETURNED","REFUNDED"})
    void everyNonNewStatusConflictsWithoutEffects(String status) throws Exception {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?",status,order); var before=state();
        request(body(0)).andExpect(status().isConflict()); assertThat(state()).isEqualTo(before); noDispatch();
    }

    @ParameterizedTest @ValueSource(strings={"role","account"})
    void stalePrincipalCannotOverridePersistedUserEligibility(String restriction) throws Exception {
        if(restriction.equals("role")) jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",buyer);
        else jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",buyer);
        var before=state(); request(body(0)).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @Test void replayWithOriginalOrCurrentVersionCannotRestoreStockQuotaOrNotifyTwice() throws Exception {
        request(body(0)).andExpect(status().isOk()); var committed=state();
        request(body(0)).andExpect(status().isConflict()); request(body(1)).andExpect(status().isConflict());
        assertThat(state()).isEqualTo(committed); assertCancelledOnce();
        assertThat(signals.hints).hasSize(2); assertThat(signals.events).hasSize(1);
        verify(broker,times(2)).convertAndSendToUser(anyString(),eq("/queue/notifications"),any(Object.class));
    }

    @ParameterizedTest @ValueSource(strings={"inbox","history","voucher"})
    void persistenceFailureRollsBackAllEffectsAndRetryCommitsOnlyOnce(String stage) throws Exception {
        var before=state();
        String table=stage.equals("inbox")?"notifications":stage.equals("history")?"order_status_history":"voucher_usages";
        jdbc.execute("ALTER TABLE uteexpress."+table+" ADD CONSTRAINT q02_cancel_fail CHECK (order_id <> "+order+") NOT VALID");
        try { request(body(0)).andExpect(status().isConflict()); }
        finally { jdbc.execute("ALTER TABLE uteexpress."+table+" DROP CONSTRAINT q02_cancel_fail"); }
        assertThat(state()).isEqualTo(before); noDispatch();
        request(body(0)).andExpect(status().isOk()); assertCancelledOnce();
    }

    @Test void standaloneAuthorityAndAccountHoldingOuterTransactionAreRejected() {
        assertThatThrownBy(() -> authority.lock(order)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var before=state();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT id FROM uteexpress.users WHERE id=? FOR UPDATE",Long.class,buyer);
            assertThatThrownBy(() -> lifecycle.cancelForBuyer(command()))
                    .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        });
        assertThat(state()).isEqualTo(before); noDispatch();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void realHttpContendersWaitOnOrderAndCommitOrRollbackReleasesExactlyOnce(boolean rollback) throws Exception {
        var before=state(); var money=immutableOrder(); var payment=rows("payments");
        var pids=new ConcurrentHashMap<String,Integer>(); var held=new CountDownLatch(1); var release=new CountDownLatch(1);
        var heldOnce=new AtomicBoolean();
        doAnswer(call -> {
            jdbc.execute("SET LOCAL lock_timeout='15s'"); jdbc.execute("SET LOCAL statement_timeout='20s'");
            pids.put(Thread.currentThread().getName(),jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
            return call.callRealMethod();
        }).when(guardSpy).lock(anyLong());
        doAnswer(call -> {
            call.callRealMethod();
            if(Thread.currentThread().getName().equals("buyer-first") && heldOnce.compareAndSet(false,true)) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(rows("notifications")).hasSize(2); assertThat(stocks()).containsExactly(10,20);
                noDispatch(); held.countDown(); assertThat(release.await(12,TimeUnit.SECONDS)).isTrue();
                if(rollback) throw new IllegalStateException("injected cancellation rollback");
            }
            return null;
        }).when(publisher).register(any());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> httpWorker("buyer-first"));
            if(!held.await(8,TimeUnit.SECONDS)) { first.get(1,TimeUnit.SECONDS); fail("first worker did not reach inbox stage"); }
            assertThat(state()).isEqualTo(before); noDispatch();
            var second=pool.submit(() -> httpWorker("buyer-second"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6); boolean blocked=false;
            while(System.nanoTime()<deadline) {
                if(pids.containsKey("buyer-second")) {
                    blocked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,pids.get("buyer-first"),pids.get("buyer-second")));
                    if(blocked) break;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(blocked).as("actual Order lock holder/waiter PIDs %s",pids).isTrue();
            assertThat(jdbc.queryForObject("SELECT query FROM pg_stat_activity WHERE pid=?",String.class,pids.get("buyer-second"))).contains("orders");
            System.out.println("Q02-F05 HTTP contention rollback="+rollback+" PIDs="+pids+" pg_blocking_pids=confirmed");
            noDispatch(); release.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(rollback?500:200);
            assertThat(second.get(15,TimeUnit.SECONDS)).isEqualTo(rollback?200:409);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
        assertCancelledOnce(); assertThat(immutableOrder()).isEqualTo(money); assertThat(rows("payments")).isEqualTo(payment);
        assertThat(signals.hints).hasSize(2); assertThat(signals.events).hasSize(1);
        verify(broker,times(2)).convertAndSendToUser(anyString(),eq("/queue/notifications"),any(Object.class));
    }

    private int httpWorker(String name) throws Exception {
        Thread.currentThread().setName(name);
        return request(body(0)).andReturn().getResponse().getStatus();
    }
    private void noDispatch() {
        assertThat(signals.hints).isEmpty(); assertThat(signals.events).isEmpty();
        verify(broker,never()).convertAndSendToUser(anyString(),eq("/queue/notifications"),any(Object.class));
    }
    private void assertCancelledOnce() {
        assertThat(row()).containsEntry("status","CANCELLED").containsEntry("version",1L);
        assertThat(stocks()).containsExactly(10,20);
        assertThat(rows("voucher_usages").getFirst()).containsEntry("status","RELEASED");
        assertThat(rows("notifications")).hasSize(2); assertThat(rows("order_status_history")).hasSize(2);
        assertThat(rows("payments").getFirst()).containsEntry("status","UNPAID"); assertThat(rows("refunds")).isEmpty();
    }
    private ResultActions request(String body) throws Exception {
        return mvc.perform(post(path()).with(user(principal(buyer,"USER"))).with(csrf())
                .contentType("application/json").accept("application/json").content(body));
    }
    private String path() { return "/buyer/orders/"+order+"/cancel"; }
    private static String body(long version) { return "{\"expectedOrderVersion\":"+version+"}"; }
    private BuyerCancellationCommand command() { return new BuyerCancellationCommand(order,0L); }
    private Map<String,Object> row() { return jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order); }
    private Map<String,Object> immutableOrder() {
        var value=row(); for(String key:List.of("status","version","updated_at","cancelled_at","cancellation_reason","inventory_released_at")) value.remove(key);
        return value;
    }
    private List<Integer> stocks() { return List.of(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?",Integer.class,product),jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?",Integer.class,secondProduct)); }
    private List<Map<String,Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM uteexpress."+table+" WHERE order_id=? ORDER BY id",order); }
    private Map<String,Object> state() { return Map.of("order",row(),"stocks",stocks(),"items",rows("order_items"),"payments",rows("payments"),"voucher",rows("voucher_usages"),"history",rows("order_status_history"),"inbox",rows("notifications"),"refunds",rows("refunds"),"shipments",rows("shipments")); }
    private long product(long shop,long category,int price,int stock) { return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Q02-F05',?,?,'ACTIVE') RETURNING id",Long.class,shop,category,price,stock); }
    private long account(String role) {
        String n="q05"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test-only','ACTIVE') RETURNING id",Long.class,n+"@example.test",n+"@example.test",n,n);
        grant(id,role); return id;
    }
    private void grant(long id,String role) { jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role); }
    private long shop(long owner) { return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Q02-F05',?,'Pickup','APPROVED') RETURNING id",Long.class,owner,UUID.randomUUID().toString()); }
    private static UteExpressPrincipal principal(long id,String role) { return new UteExpressPrincipal(id,"q05-"+id,null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true); }
    private static void auth(long id,String role) { var p=principal(id,role); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities())); }
}
