package com.uteexpress.notification;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.notification.service.NotificationService;
import com.uteexpress.notification.service.OrderNotificationListener;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.OrderStatusChangedEvent;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.util.List;
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

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class NotificationIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
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
