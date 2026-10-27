package com.uteexpress.integration;

import com.uteexpress.common.exception.*;
import com.uteexpress.fulfillment.dto.*;
import com.uteexpress.fulfillment.service.*;
import com.uteexpress.order.dto.*;
import com.uteexpress.security.SecurityContextCurrentUserProvider;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shipping.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Timeout(50)
class ReturnToSenderIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired ShipperFulfillmentService fulfillment;
    @Autowired DeliveryAttemptCoordinator attempts;
    @Autowired ShipperAssignmentReadService shipperReads;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @MockitoSpyBean ReturnToSenderService returns;
    @MockitoSpyBean SecurityContextCurrentUserProvider users;
    long admin, manager, shipper, buyer, vendor, product, order;

    @BeforeEach void fixture() {
        admin=account("ADMIN"); manager=account("MANAGER"); shipper=account("SHIPPER"); buyer=account("USER"); vendor=account("VENDOR");
        long shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'RTS',?,'Pickup','APPROVED') RETURNING id",Long.class,vendor,UUID.randomUUID().toString());
        long category=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('RTS',?,true) RETURNING id",Long.class,UUID.randomUUID().toString());
        product=jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'RTS',100000,9,'ACTIVE') RETURNING id",Long.class,shop,category);
        long provider=jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'RTS',true) RETURNING id",Long.class,"R"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT));
        String key=UUID.randomUUID().toString();
        order=jdbc.queryForObject("""
                INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,
                    province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,commission_amount,
                    commission_rate_snapshot,shipping_provider_id,shipping_service_code,ready_at)
                VALUES (?,?,'hash',?,?,'CONFIRMED','Test','0900000000','VN','District','Test',100000,10000,15000,105000,6300,7,?,'STANDARD',CURRENT_TIMESTAMP) RETURNING id
                """,Long.class,key,key,buyer,shop,provider);
        jdbc.update("INSERT INTO uteexpress.order_items(order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,final_unit_price,line_total,quantity) VALUES (?,?,'RTS',100000,0,100000,100000,1)",order,product);
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',105000,?)",order,key);
        long voucher=jdbc.queryForObject("""
                INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by)
                VALUES (?,'SHOP','FIXED',?,10000,0,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day',10,2,?) RETURNING id
                """,Long.class,"R"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT),shop,vendor);
        jdbc.update("UPDATE uteexpress.orders SET voucher_id=?,voucher_code=(SELECT code FROM uteexpress.vouchers WHERE id=?),voucher_scope='SHOP' WHERE id=?",voucher,voucher,order);
        jdbc.update("INSERT INTO uteexpress.voucher_usages(voucher_id,user_id,order_id,status,discount_amount) VALUES (?,?,?,'REDEEMED',10000)",voucher,buyer,order);
        auth(admin,"ADMIN"); assignments.assign(order,shipper,0L);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER"})
    void opsReceiptCancelsReleasesExactlyOnceAndReplaysWithoutChangingMoney(String role) throws Exception {
        exhaust(); var command=command(); var payment=rows("payments");
        auth(role.equals("ADMIN")?admin:manager,role);
        var result=lifecycle.confirmReturnToSender(command);
        assertThat(result.replayed()).isFalse(); assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        var after=state();
        assertThat(lifecycle.confirmReturnToSender(command).replayed()).isTrue();
        assertThat(state()).isEqualTo(after);
        assertThat(stock()).isEqualTo(10); assertThat(rows("payments")).isEqualTo(payment);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.voucher_usages WHERE order_id=?",String.class,order)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?",String.class,order)).isEqualTo("RETURNED_TO_SENDER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND from_status='SHIPPING' AND to_status='CANCELLED'",Long.class,order)).isEqualTo(1);
        assertThat(rows("shipment_return_receipts")).hasSize(1); assertThat(rows("refunds")).isEmpty();
        assertThatThrownBy(() -> lifecycle.confirmReturnToSender(new ReturnToSenderCommand(order,command.expectedOrderVersion(),command.expectedShipmentVersion(),"changed")))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(state()).isEqualTo(after);
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",role.equals("ADMIN")?admin:manager);
        assertThatThrownBy(() -> lifecycle.confirmReturnToSender(command))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThat(state()).isEqualTo(after);
    }

    @Test void firstFailureCannotCancelAndTwoFailuresKeepStockUntilOpsAttests() {
        start(); fail(); var first=state(); auth(admin,"ADMIN");
        assertThatThrownBy(() -> lifecycle.confirmReturnToSender(command())).isInstanceOf(ApplicationException.class);
        assertThat(state()).isEqualTo(first); assertThat(stock()).isEqualTo(9);
        auth(shipper,"SHIPPER"); attempts.retryDelivery(order,new RetryDeliveryRequest(ov(),sv())); fail();
        var failed=state();
        assertThatThrownBy(() -> attempts.retryDelivery(order,new RetryDeliveryRequest(ov(),sv()))).isInstanceOf(ApplicationException.class);
        assertThat(state()).isEqualTo(failed); assertThat(stock()).isEqualTo(9);
    }

    @Test void terminalMetricUsesRealShipmentPathsNotAttemptsOrOpenFailuresAndIsTenantScoped() {
        long commonShipper=shipper; // Initial fixture remains ASSIGNED and is excluded from outcomes.
        sameShipperFixture(commonShipper); start();
        fulfillment.transition(order,OrderAction.DELIVER,new FulfillmentRequest(ov(),sv(),new java.math.BigDecimal("105000")));
        sameShipperFixture(commonShipper); start(); fail();
        attempts.retryDelivery(order,new RetryDeliveryRequest(ov(),sv()));
        fulfillment.transition(order,OrderAction.DELIVER,new FulfillmentRequest(ov(),sv(),new java.math.BigDecimal("105000")));
        sameShipperFixture(commonShipper); exhaust(); // Exhausted but not physically returned: no terminal outcome yet.
        assertThat(stock()).isEqualTo(9);
        for(int i=0;i<3;i++) {
            sameShipperFixture(commonShipper); exhaust(); var c=command(); auth(admin,"ADMIN");
            lifecycle.confirmReturnToSender(c);
            assertThat(stock()).isEqualTo(10);
        }
        sameShipperFixture(commonShipper); start(); // One in-progress first attempt, excluded.
        fixture(); long foreignShipper=shipper; start();
        fulfillment.transition(order,OrderAction.DELIVER,new FulfillmentRequest(ov(),sv(),new java.math.BigDecimal("105000")));
        auth(commonShipper,"SHIPPER"); var metrics=shipperReads.dashboard();
        assertThat(metrics.total()).isEqualTo(8);
        assertThat(List.of(metrics.assigned(),metrics.shipping(),metrics.delivered(),metrics.failed(),metrics.returnedToSender()))
                .containsExactly(1L,1L,2L,1L,3L);
        assertThat(metrics.successRate()).isEqualByComparingTo("40.0");
        auth(foreignShipper,"SHIPPER");
        assertThat(shipperReads.dashboard().total()).isEqualTo(1);
        assertThat(shipperReads.dashboard().successRate()).isEqualByComparingTo("100.0");
        auth(account("SHIPPER"),"SHIPPER"); assertThat(shipperReads.dashboard().successRate()).isNull();
    }

    @ParameterizedTest @ValueSource(strings={"USER","VENDOR","SHIPPER"})
    void nonOpsAndForgedOwnershipHaveNoEffects(String role) throws Exception {
        exhaust(); var before=state(); long actor=role.equals("USER")?buyer:role.equals("VENDOR")?vendor:shipper;
        mvc.perform(post(path()).with(user(principal(actor,role))).with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before);
    }

    @Test void httpCsrfUnknownFieldsStaleAndPersistedRoleRestrictionsFailClosed() throws Exception {
        exhaust(); var command=command(); String input=body(); var before=state();
        mvc.perform(post(path()).with(user(principal(admin,"ADMIN"))).contentType("application/json").content(input)).andExpect(status().isForbidden());
        mvc.perform(post(path()).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json")
                .content(input.replace("}",",\"actorId\":1,\"stock\":100}"))).andExpect(status().isBadRequest());
        auth(admin,"ADMIN");
        assertThatThrownBy(() -> lifecycle.confirmReturnToSender(new ReturnToSenderCommand(order,command.expectedOrderVersion()+1,command.expectedShipmentVersion(),"test"))).isInstanceOf(ApplicationException.class);
        assertThat(state()).isEqualTo(before);
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",admin);
        mvc.perform(post(path()).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(input)).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"PAID","MISSING","AMOUNT","ONLINE"})
    void inconsistentPaymentNeverGetsRewrittenOrCancels(String invalid) {
        exhaust();
        switch(invalid) {
            case "PAID" -> jdbc.update("UPDATE uteexpress.payments SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE order_id=?",order);
            case "MISSING" -> jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?",order);
            case "ONLINE" -> jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?",order);
            default -> jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=?",order);
        }
        var before=state(); auth(admin,"ADMIN");
        assertThatThrownBy(() -> lifecycle.confirmReturnToSender(command())).isInstanceOf(ApplicationException.class);
        assertThat(state()).isEqualTo(before);
    }

    @Test void intermediateHistoryFailureRollsBackReceiptShipmentStockQuotaAndInboxThenRetries() {
        exhaust(); var command=command(); var before=state(); auth(admin,"ADMIN");
        jdbc.execute("ALTER TABLE uteexpress.order_status_history ADD CONSTRAINT rts_reject_history CHECK (order_id <> "+order+" OR to_status <> 'CANCELLED') NOT VALID");
        try { assertThatThrownBy(() -> lifecycle.confirmReturnToSender(command)).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE uteexpress.order_status_history DROP CONSTRAINT rts_reject_history"); }
        assertThat(state()).isEqualTo(before);
        assertThat(lifecycle.confirmReturnToSender(command).replayed()).isFalse();
        assertThat(stock()).isEqualTo(10);
    }

    @Test void successfulReceiptIsImmutableAndOuterTransactionIsRejected() {
        exhaust(); auth(admin,"ADMIN"); var c=command();
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                assertThatThrownBy(() -> lifecycle.confirmReturnToSender(c)).isInstanceOf(ApplicationException.class));
        lifecycle.confirmReturnToSender(c);
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.shipment_return_receipts SET receipt_reference='changed' WHERE order_id=?",order)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM uteexpress.shipment_return_receipts WHERE order_id=?",order)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE uteexpress.shipment_return_receipts")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void concurrentSameReceiptWaitsForOrderAndCommitOrRollbackHasOneRelease(boolean rollback) throws Exception {
        exhaust(); var c=command(); var held=new CountDownLatch(1); var release=new CountDownLatch(1);
        var waiterEntered=new CountDownLatch(1); var holderPid=new AtomicInteger(); var waiterPid=new AtomicInteger();
        ReturnToSenderService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(returns);
        SecurityContextCurrentUserProvider userTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(users);
        var firstReceipt=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            call.callRealMethod();
            if(firstReceipt.getAndSet(false)) {
                holderPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                held.countDown(); assertThat(release.await(12,TimeUnit.SECONDS)).isTrue();
                if(rollback) throw new ApplicationException(ErrorCode.CONFLICT);
            }
            return null;
        }).when(target).receive(any(),anyLong(),anyLong(),anyString(),any());
        doAnswer(call -> {
            if(Thread.currentThread().getName().equals("rts-waiter")) {
                waiterPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class)); waiterEntered.countDown();
            }
            return call.callRealMethod();
        }).when(userTarget).currentUser();
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> { auth(admin,"ADMIN"); try { return lifecycle.confirmReturnToSender(c); } finally { SecurityContextHolder.clearContext(); } });
            if (!held.await(10,TimeUnit.SECONDS)) {
                first.get(2,TimeUnit.SECONDS); // Surface the actual worker failure instead of only a latch assertion.
                org.assertj.core.api.Assertions.fail("receipt worker did not reach the held stage");
            }
            var second=pool.submit(() -> { Thread.currentThread().setName("rts-waiter"); auth(admin,"ADMIN"); try { return lifecycle.confirmReturnToSender(c); } finally { SecurityContextHolder.clearContext(); } });
            assertThat(waiterEntered.await(8,TimeUnit.SECONDS)).isTrue();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5); boolean blocked=false;
            while(System.nanoTime()<deadline) {
                blocked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,holderPid.get(),waiterPid.get()));
                if(blocked) break;
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(blocked).as("second Ops receipt waits on actual Order lock").isTrue();
            assertThat(rows("shipment_return_receipts")).isEmpty(); assertThat(stock()).isEqualTo(9);
            release.countDown();
            if(rollback) assertThatThrownBy(() -> first.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(ApplicationException.class);
            else assertThat(first.get(15,TimeUnit.SECONDS).replayed()).isFalse();
            assertThat(second.get(15,TimeUnit.SECONDS).replayed()).isEqualTo(!rollback);
            assertThat(rows("shipment_return_receipts")).hasSize(1); assertThat(stock()).isEqualTo(10);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    private long account(String role) {
        String n="rts"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test-only','ACTIVE') RETURNING id",Long.class,n+"@example.test",n+"@example.test",n,n);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role); return id;
    }
    private void sameShipperFixture(long commonShipper) {
        fixture(); auth(admin,"ADMIN"); assignments.reassign(order,commonShipper,sv()); shipper=commonShipper;
    }
    private UteExpressPrincipal principal(long id,String role) { return new UteExpressPrincipal(id,"rts",null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true); }
    private void auth(long id,String role) { var p=principal(id,role); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities())); }
    private long ov() { return jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?",Long.class,order); }
    private long sv() { return jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?",Long.class,order); }
    private int stock() { return jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?",Integer.class,product); }
    private void start() { auth(shipper,"SHIPPER"); fulfillment.transition(order,OrderAction.PICK_UP,new FulfillmentRequest(ov(),sv(),null)); fulfillment.transition(order,OrderAction.START_SHIPPING,new FulfillmentRequest(ov(),sv(),null)); }
    private void fail() { attempts.reportFailure(order,new DeliveryFailureRequest(ov(),sv(),"Recipient unavailable","TEST-ATTEMPT")); }
    private void exhaust() { start(); fail(); attempts.retryDelivery(order,new RetryDeliveryRequest(ov(),sv())); fail(); }
    private ReturnToSenderCommand command() { return new ReturnToSenderCommand(order,ov(),sv(),"TEST-PHYSICAL-RECEIPT"); }
    private String path() { return "/admin/orders/"+order+"/return-to-sender"; }
    private String body() { return "{\"expectedOrderVersion\":"+ov()+",\"expectedShipmentVersion\":"+sv()+",\"receiptReference\":\"TEST-PHYSICAL-RECEIPT\"}"; }
    private List<Map<String,Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM uteexpress."+table+" WHERE order_id=? ORDER BY id",order); }
    private Map<String,Object> state() {
        return Map.of("order",jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order),"stock",stock(),
                "shipment",rows("shipments"),"payments",rows("payments"),"usage",rows("voucher_usages"),
                "history",rows("order_status_history"),"receipt",rows("shipment_return_receipts"),"inbox",rows("notifications"));
    }
}
