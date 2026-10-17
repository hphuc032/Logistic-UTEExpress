package com.uteexpress.notification;

import com.uteexpress.notification.service.NotificationProjectionService;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.order.dto.*;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Testcontainers
class NotificationRecoveryIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationProjectionService projection;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired PlatformTransactionManager manager;
    long buyer, owner, order;
    @BeforeEach void setup() {
        buyer=account("USER"); owner=account("VENDOR"); order=createOrder(buyer,owner); auth(owner);
    }
    private long createOrder(long buyerId,long ownerId) {
        long shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Recovery shop',?,'Pickup','APPROVED') RETURNING id",Long.class,ownerId,UUID.randomUUID().toString());
        String key=UUID.randomUUID().toString();
        long id=jdbc.queryForObject("INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot) VALUES (?,?,'hash',?,?,'NEW','Receiver','0900000000','VN','District','Street',100,0,0,100,0,0) RETURNING id",Long.class,key,key,buyerId,shop);
        jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id) VALUES (?,NULL,'NEW',?)",id,buyerId);
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',100,?)",id,key);
        return id;
    }
    private void auth(long id) {
        var principal=new UteExpressPrincipal(id,"owner",null,0,List.of(new SimpleGrantedAuthority("ROLE_VENDOR")),true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void notificationFailureDoesNotFailCommittedOrderAndReconciliationRecovers() {
        jdbc.execute("ALTER TABLE uteexpress.notifications ADD CONSTRAINT notice_fail CHECK (order_id <> "+order+") NOT VALID");
        try {
            var event=lifecycle.transition(new OrderTransitionCommand(order,OrderStatus.NEW,0L,OrderAction.CONFIRM,null));
            assertThat(event.toStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?",String.class,order)).isEqualTo("CONFIRMED");
        } finally { jdbc.execute("ALTER TABLE uteexpress.notifications DROP CONSTRAINT notice_fail"); }
        projection.reconcileBatch();
        assertThat(count()).isEqualTo(2);
        assertThat(projection.projectOrder(order)).isZero();
        assertThat(jdbc.queryForList("SELECT recipient_id FROM uteexpress.notifications WHERE order_id=? ORDER BY recipient_id",Long.class,order)).containsExactlyInAnyOrder(buyer,owner);
        assertThat(jdbc.queryForList("SELECT message FROM uteexpress.notifications WHERE order_id=?",String.class,order))
                .allSatisfy(message -> assertThat(message).contains("CONFIRMED").doesNotContain("0900000000","Street","hash"));
    }

    @Test void failedBusinessTransitionLeavesNoNotificationSource() {
        jdbc.execute("ALTER TABLE uteexpress.order_status_history ADD CONSTRAINT notice_history_fail CHECK (order_id <> "+order+" OR to_status <> 'CONFIRMED') NOT VALID");
        try { assertThatThrownBy(() -> lifecycle.transition(new OrderTransitionCommand(order,OrderStatus.NEW,0L,OrderAction.CONFIRM,null)))
                .isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE uteexpress.order_status_history DROP CONSTRAINT notice_history_fail"); }
        projection.reconcileBatch();
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?",String.class,order)).isEqualTo("NEW");
    }

    @Test @Timeout(40) void concurrentWorkersCreateExactlyOneRowPerHistoryAndRecipient() throws Exception {
        history("NEW","CONFIRMED");
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Integer> worker=()->{ start.await(); return projection.projectOrder(order); };
        try {
            var first=pool.submit(worker); var second=pool.submit(worker); start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(0,2);
            assertThat(count()).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }

    @Test @Timeout(40) void lateCommitWithLowerHistoryIdIsRecoveredWithoutHighWaterCursor() throws Exception {
        long otherOwner=account("VENDOR"), otherOrder=createOrder(account("USER"),otherOwner);
        var inserted=new CountDownLatch(1); var release=new CountDownLatch(1); var low=new AtomicLong();
        var pool=Executors.newSingleThreadExecutor();
        jdbc.execute("ALTER TABLE uteexpress.notifications ADD CONSTRAINT notice_late_fail CHECK (order_id <> "+order+") NOT VALID");
        try {
            var pending=pool.submit(()->{
                auth(owner);
                try { new TransactionTemplate(manager).executeWithoutResult(s->{
                    lifecycle.transition(new OrderTransitionCommand(order,OrderStatus.NEW,0L,OrderAction.CONFIRM,null));
                    low.set(jdbc.queryForObject("SELECT max(id) FROM uteexpress.order_status_history WHERE order_id=?",Long.class,order));
                    inserted.countDown();
                    try { assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); }
                    catch(InterruptedException ex){Thread.currentThread().interrupt(); throw new AssertionError(ex);}
                }); } finally {SecurityContextHolder.clearContext();}
            });
            assertThat(inserted.await(10,TimeUnit.SECONDS)).isTrue();
            auth(otherOwner);
            lifecycle.transition(new OrderTransitionCommand(otherOrder,OrderStatus.NEW,0L,OrderAction.CONFIRM,null));
            long high=jdbc.queryForObject("SELECT max(id) FROM uteexpress.order_status_history WHERE order_id=?",Long.class,otherOrder);
            assertThat(high).isGreaterThan(low.get());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=?",Integer.class,otherOrder)).isEqualTo(2);
            projection.reconcileBatch(); assertThat(count()).isZero(); // Lower history is uncommitted.
            release.countDown(); pending.get(15,TimeUnit.SECONDS); // Lower-ID Order commits; its callback fails.
            assertThat(count()).isZero();
        } finally {
            release.countDown(); pool.shutdownNow(); jdbc.execute("ALTER TABLE uteexpress.notifications DROP CONSTRAINT notice_late_fail");
        }
        projection.reconcileBatch(); assertThat(count()).isEqualTo(2);
        assertThat(projection.projectOrder(order)).isZero();
    }
    @Test void recipientOverlapDeduplicatesAndProjectionUsesPersistedFactsOnly() {
        jdbc.update("UPDATE uteexpress.orders SET buyer_id=? WHERE id=?",owner,order);
        history("NEW","CONFIRMED");
        assertThat(projection.projectOrder(order)).isEqualTo(1);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT message FROM uteexpress.notifications WHERE order_id=?",String.class,order))
                .doesNotContain("sensitive source reason");
    }
    @Test void failedOrderDoesNotBlockOtherOrdersAndRemainsRetryable() {
        long otherOwner = account("VENDOR"), otherOrder = createOrder(account("USER"), otherOwner);
        assertThat(otherOrder).isGreaterThan(order); // Discovery orders by Order ID.
        history("NEW", "CONFIRMED");
        jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id) VALUES (?,'NEW','CONFIRMED',?)",
                otherOrder, otherOwner);
        // Fail one recipient after the first recipient insert, proving the whole Order rolls back.
        jdbc.execute("ALTER TABLE uteexpress.notifications ADD CONSTRAINT notice_batch_fail CHECK (order_id <> "
                + order + " OR recipient_id <> " + owner + ") NOT VALID");
        try {
            assertThat(projection.reconcileBatch()).isGreaterThanOrEqualTo(2);
            assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=?",
                    Integer.class, otherOrder)).isEqualTo(2);
            assertThat(projection.reconcileBatch()).isZero();
            assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=?",
                    Integer.class, otherOrder)).isEqualTo(2);
        } finally {
            jdbc.execute("ALTER TABLE uteexpress.notifications DROP CONSTRAINT notice_batch_fail");
        }
        assertThat(projection.reconcileBatch()).isEqualTo(2);
        assertThat(count()).isEqualTo(2);
        assertThat(projection.reconcileBatch()).isZero();
    }
    private long history(String from,String to) {
        return jdbc.queryForObject("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id,reason) VALUES (?,?,?,?, 'sensitive source reason') RETURNING id",Long.class,order,from,to,owner);
    }
    private int count(){return jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=?",Integer.class,order);}
    private long account(String role) {
        String name=UUID.randomUUID().toString().replace("-", "").substring(0,20);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test','ACTIVE') RETURNING id",Long.class,name+"@test.local",name+"@test.local",name,name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role); return id;
    }
}
