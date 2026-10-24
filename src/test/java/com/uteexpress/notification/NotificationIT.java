package com.uteexpress.notification;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.notification.service.NotificationService;
import com.uteexpress.notification.service.OrderNotificationListener;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.OrderStatusChangedEvent;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.util.List;
import com.uteexpress.notification.dto.NotificationCommitted;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Timeout;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(NotificationIT.HintConfiguration.class)
class NotificationIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @TestConfiguration(proxyBeanMethods = false)
    static class HintConfiguration {
        @Bean HintCollector hintCollector() { return new HintCollector(); }
    }
    static class HintCollector {
        final List<NotificationCommitted> received = new CopyOnWriteArrayList<>();
        @EventListener public void receive(NotificationCommitted event) { received.add(event); }
    }
    @Autowired HintCollector hints;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Autowired OrderNotificationListener orderListener;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;

    @Test void deduplicationOwnershipAndIdempotentRead() throws Exception {
        Long owner = createUser();
        Long other = createUser();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifications.record(owner, null, "SYSTEM", "Thông báo", "Nội dung an toàn", "notification-it:" + owner);
            notifications.record(owner, null, "SYSTEM", "Thông báo", "Nội dung an toàn", "notification-it:" + owner);
        });
        Long id = jdbc.queryForObject("SELECT id FROM uteexpress.notifications WHERE dedup_key=?",
                Long.class, "notification-it:" + owner);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE dedup_key=?",
                Integer.class, "notification-it:" + owner)).isEqualTo(1);
        mvc.perform(get("/notifications").with(user(principal(owner)))).andExpect(status().isOk())
                .andExpect(view().name("notification/inbox"));
        mvc.perform(post("/notifications/{id}/read", id).with(user(principal(other))).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/notifications/{id}/read", id).with(user(principal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/notifications/{id}/read", id).with(user(principal(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var firstRead = jdbc.queryForObject("SELECT read_at FROM uteexpress.notifications WHERE id=?",
                java.sql.Timestamp.class, id);
        mvc.perform(post("/notifications/{id}/read", id).with(user(principal(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT read_at FROM uteexpress.notifications WHERE id=?",
                java.sql.Timestamp.class, id)).isEqualTo(firstRead);
    }

    @Test void notificationWriteRollsBackWithBusinessTransaction() {
        Long owner = createUser();
        String key = "rollback-it:" + owner;
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifications.record(owner, null, "SYSTEM", "Rollback", "Should disappear", key);
            throw new IllegalStateException("abort business operation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE dedup_key=?",
                Integer.class, key)).isZero();
        assertThatThrownBy(() -> notifications.record(owner, null, "SYSTEM", "No tx", "Denied", key))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test void orderEventTargetsOnlyBuyerAndShopOwnerAndDeduplicatesRetry() {
        Long buyer = createUser();
        Long owner = createUser();
        Long stranger = createUser();
        String slug = "notice-" + System.nanoTime();
        Long shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops (owner_id, name, slug, pickup_address, status)
                VALUES (?, 'Notification shop', ?, 'Test address', 'APPROVED') RETURNING id
                """, Long.class, owner, slug);
        String key = "notice-order-" + System.nanoTime();
        Long order = jdbc.queryForObject("""
                INSERT INTO uteexpress.orders (order_code, checkout_key, request_hash, buyer_id, shop_id,
                    status, receiver_name, phone, province_code, district, detail, subtotal,
                    discount_total, shipping_fee, grand_total, commission_amount, commission_rate_snapshot)
                VALUES (?, ?, 'request', ?, ?, 'NEW', 'Buyer', '0123456789', 'HCM', 'District',
                    'Street', 100, 0, 0, 100, 0, 0) RETURNING id
                """, Long.class, key, key, buyer, shop);
        var event = new OrderStatusChangedEvent(UUID.randomUUID(), order, OrderStatus.NEW,
                OrderStatus.CONFIRMED, buyer, Instant.now(), null);
        orderListener.onOrderStatusChanged(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=?", Integer.class, order)).isZero();
        jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id) VALUES (?,'NEW','CONFIRMED',?)",order,buyer);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { orderListener.onOrderStatusChanged(event); }
                }));
        orderListener.onOrderStatusChanged(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=?",
                Integer.class, order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=? AND recipient_id=?",
                Integer.class, order, buyer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=? AND recipient_id=?",
                Integer.class, order, owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=? AND recipient_id=?",
                Integer.class, order, stranger)).isZero();
        var committedRows = jdbc.query("SELECT id, recipient_id FROM uteexpress.notifications WHERE order_id=?",
                (rs, ignored) -> new NotificationCommitted(rs.getLong(1), rs.getLong(2)), order);
        assertThat(hints.received.stream().filter(e -> committedRows.contains(e)).toList())
                .containsExactlyInAnyOrderElementsOf(committedRows);
        jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=?", order);
        jdbc.update("DELETE FROM uteexpress.orders WHERE id=?", order);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id IS NULL AND recipient_id IN (?, ?)",
                Integer.class, buyer, owner)).isEqualTo(2);
    }

    @Test void realtimeHintsFollowCommitAndNeverDuplicateOrEscapeRollback() {
        Long owner = createUser();
        String key = "committed-hint:" + owner;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifications.record(owner, null, "SYSTEM", "Safe title", "Safe content", key);
            notifications.record(owner, null, "SYSTEM", "Safe title", "Safe content", key);
            assertThat(hints.received).noneMatch(e -> e.recipientId().equals(owner));
        });
        Long id = jdbc.queryForObject("SELECT id FROM uteexpress.notifications WHERE dedup_key=?", Long.class, key);
        assertThat(hints.received.stream().filter(e -> e.recipientId().equals(owner)).toList())
                .containsExactly(new NotificationCommitted(id, owner));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifications.record(owner, null, "SYSTEM", "Safe title", "Safe content", key + ":rollback");
            status.setRollbackOnly();
        });
        assertThat(hints.received.stream().filter(e -> e.recipientId().equals(owner)).toList()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE dedup_key=?",
                Integer.class, key + ":rollback")).isZero();
    }

    @Test @Timeout(40) void concurrentDuplicatePublishesOnlyCommittedWinner() throws Exception {
        raceDeduplicatedInsert(false);
    }

    @Test @Timeout(40) void rolledBackFirstInsertAllowsWaitingWriterToPublishOnce() throws Exception {
        raceDeduplicatedInsert(true);
    }

    private void raceDeduplicatedInsert(boolean rollbackFirst) throws Exception {
        Long owner = createUser();
        String key = "concurrent-hint:" + owner;
        var inserted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var firstPid = new AtomicInteger();
        var secondPid = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                var tx = new TransactionTemplate(transactionManager);
                tx.setTimeout(20);
                tx.executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '15s'");
                    firstPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    notifications.record(owner, null, "SYSTEM", "Race", "Safe content", key);
                    inserted.countDown();
                    await(releaseFirst);
                    if (rollbackFirst) status.setRollbackOnly();
                });
            });
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> {
                var tx = new TransactionTemplate(transactionManager);
                tx.setTimeout(20);
                tx.executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '15s'");
                    secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    secondStarted.countDown();
                    notifications.record(owner, null, "SYSTEM", "Race", "Safe content", key);
                });
            });
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            boolean blocked = false;
            while (System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, firstPid.get(), secondPid.get()));
                if (blocked) break;
                Thread.sleep(20);
            }
            assertThat(blocked).as("PostgreSQL confirms second insert waits for first transaction").isTrue();
            assertThat(hints.received).noneMatch(e -> e.recipientId().equals(owner));
            releaseFirst.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            var rows = jdbc.query("SELECT id, recipient_id FROM uteexpress.notifications WHERE dedup_key=?",
                    (rs, ignored) -> new NotificationCommitted(rs.getLong(1), rs.getLong(2)), key);
            assertThat(rows).hasSize(1);
            assertThat(hints.received.stream().filter(e -> e.recipientId().equals(owner)).toList())
                    .containsExactlyElementsOf(rows);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private Long createUser() {
        String name = "notice" + System.nanoTime();
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.users (email, normalized_email, username, normalized_username,
                    password_hash, status) VALUES (?, ?, ?, ?, 'not-a-password', 'ACTIVE') RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name);
    }

    private UteExpressPrincipal principal(Long id) {
        return new UteExpressPrincipal(id, "notice", null, 0,
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
}
