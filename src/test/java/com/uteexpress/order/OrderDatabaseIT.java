package com.uteexpress.order;

import com.uteexpress.checkout.dto.*;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.order.service.OrderLifecycleServiceImpl;
import com.uteexpress.payment.entity.Payment;
import com.uteexpress.payment.repository.PaymentRepository;
import com.uteexpress.security.CurrentUser;
import com.uteexpress.security.CurrentUserProvider;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OrderDatabaseIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired PaymentRepository payments;
    Long buyer;
    final List<Object> events = new ArrayList<>();
    static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM uteexpress.order_status_history");
        jdbc.update("DELETE FROM uteexpress.payments");
        jdbc.update("DELETE FROM uteexpress.order_items");
        jdbc.update("DELETE FROM uteexpress.orders");
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        buyer = jdbc.queryForObject("""
                INSERT INTO uteexpress.users
                (email,normalized_email,username,normalized_username,password_hash,status)
                VALUES (?,?,?,?, 'test-only-password', 'ACTIVE') RETURNING id
                """, Long.class, key + "@example.test", key + "@example.test", key, key);
    }

    CheckoutQuote quote() {
        return new CheckoutQuote(901L, List.of(new CheckoutQuote.ItemSnapshot(902L, "Snapshot",
                new BigDecimal("12.00"), new BigDecimal("2.00"), new BigDecimal("10.00"),
                2, new BigDecimal("20.00"))),
                new CheckoutQuote.AddressSnapshot("Receiver", "0900000000", "79", "District", "Detail"),
                OrderTotals.calculate(new BigDecimal("20.00"), new BigDecimal("3.00"), new BigDecimal("5.00")),
                903L, "Standard", 904L, new BigDecimal("10.123456"), new BigDecimal("2.00"));
    }

    // Controlled trusted guards exercise persistence, not production authorization.
    OrderLifecycleServiceImpl lifecycle(Long actor) {
        return new OrderLifecycleServiceImpl(orders, items, history,
                new CurrentUserProvider() {
                    public boolean isAuthenticated() { return true; }
                    public Optional<CurrentUser> currentUser() {
                        return Optional.of(new CurrentUser("opaque", Set.of(), Set.of()));
                    }
                },
                transactionManager, events::add, Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override protected Long authorizeCreation(CurrentUser user, CheckoutQuote quote) { return buyer; }
            @Override protected Authorization authorize(CurrentUser user, Order order, OrderTransitionCommand command) {
                return new Authorization(actor, "Approved test guard");
            }
        };
    }
    Order create() {
        String key = UUID.randomUUID().toString();
        return lifecycle(buyer).createNew(key, key, "hash", quote());
    }
    OrderTransitionCommand confirm(Order order) {
        return new OrderTransitionCommand(order.getId(), OrderStatus.NEW, order.getVersion(), OrderAction.CONFIRM, null);
    }

    @Test void flywayAndHibernateValidateRealSchemaWithExactlyFiveForeignKeys() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(emf.getProperties().get("hibernate.hbm2ddl.auto")).isEqualTo("validate");
        assertThat(jdbc.queryForList("""
                SELECT conname FROM pg_constraint WHERE contype='f' AND conrelid IN
                ('uteexpress.orders'::regclass,'uteexpress.order_items'::regclass,
                 'uteexpress.payments'::regclass,'uteexpress.order_status_history'::regclass)
                """, String.class)).containsExactlyInAnyOrder("fk_orders_buyer_id", "fk_order_items_order_id",
                "fk_payments_order_id", "fk_order_status_history_order_id", "fk_order_status_history_actor_id");
        assertThat(jdbc.queryForObject("SELECT to_regclass(?)::text", String.class, "uteexpress.shops"))
                .isEqualTo("uteexpress.shops");
        for (String parent : List.of("products", "commission_policies")) {
            assertThat(jdbc.queryForObject("SELECT to_regclass(?)::text", String.class, "uteexpress." + parent)).isNull();
        }
    }

    @Test void persistsAllEntitiesSnapshotsAndVersionAndPublishesAfterCommit() {
        Order order = create();
        assertThat(order.getVersion()).isZero();
        Payment payment = payments.saveAndFlush(new Payment(order.getId(), CheckoutRequest.PaymentMethod.ONLINE,
                quote().totals(), "attempt", NOW));
        assertThat(payments.findById(payment.getId()).orElseThrow().getAmount()).isEqualByComparingTo("22.00");
        assertThat(items.findAll().getFirst().getLineTotal()).isEqualByComparingTo("20.00");
        assertThat(history.findAll().getFirst().getFromStatus()).isNull();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            lifecycle(buyer).transition(confirm(order));
            assertThat(events).isEmpty();
        });
        Order stored = orders.findById(order.getId()).orElseThrow();
        assertThat(stored.getVersion()).isEqualTo(1L);
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(stored.getCommissionRateSnapshot()).isEqualByComparingTo("10.123456");
        assertThat(history.count()).isEqualTo(2);
        assertThat(events).hasSize(1);
        history.saveAndFlush(new OrderStatusHistory(order.getId(), OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED, null, NOW, "System actor"));
        assertThat(history.findAll()).anyMatch(row -> row.getActorId() == null);
    }

    @Test void historyFailureAndOuterRollbackUndoStatusVersionHistoryAndEvents() {
        Order order = create();
        assertThatThrownBy(() -> lifecycle(Long.MAX_VALUE).transition(confirm(order)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertUnchanged(order);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            lifecycle(buyer).transition(confirm(order));
            tx.setRollbackOnly();
        });
        assertUnchanged(order);
    }
    void assertUnchanged(Order order) {
        Order stored = orders.findById(order.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(stored.getVersion()).isZero();
        assertThat(history.count()).isEqualTo(1);
        assertThat(events).isEmpty();
    }

    @Test void independentSessionsRejectStaleVersion() {
        Order order = create();
        try (var first = emf.createEntityManager(); var second = emf.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Order a = first.find(Order.class, order.getId());
            Order b = second.find(Order.class, order.getId());
            a.applyValidatedTransition(OrderStatus.CONFIRMED, NOW, null);
            first.getTransaction().commit();
            b.applyValidatedTransition(OrderStatus.CANCELLED, NOW, "Stale writer");
            assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
            second.getTransaction().rollback();
        }
        assertThat(orders.findById(order.getId()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test void rejectsInvalidMoneyEnumsAndAllExistingParentViolations() {
        Order order = create();
        payments.saveAndFlush(new Payment(order.getId(), CheckoutRequest.PaymentMethod.COD, quote().totals(), "attempt", NOW));
        for (String assignment : List.of("buyer_id=9223372036854775807", "status='DELIVERY_FAILED'",
                "subtotal=-1", "shipping_fee=-1", "discount_total=21", "grand_total=23",
                "commission_amount=18", "commission_rate_snapshot=101", "version=-1", "subtotal='NaN'")) {
            rejects("UPDATE uteexpress.orders SET " + assignment);
        }
        for (String assignment : List.of("order_id=9223372036854775807", "quantity=0", "unit_price=0",
                "discount_snapshot=13", "final_unit_price=11", "line_total=21", "product_id=0")) {
            rejects("UPDATE uteexpress.order_items SET " + assignment);
        }
        for (String assignment : List.of("order_id=9223372036854775807", "status='FAILED'", "method='CARD'", "amount=-1", "amount='NaN'")) {
            rejects("UPDATE uteexpress.payments SET " + assignment);
        }
        for (String assignment : List.of("order_id=9223372036854775807", "actor_id=9223372036854775807",
                "from_status='INVALID'", "to_status='INVALID'")) {
            rejects("UPDATE uteexpress.order_status_history SET " + assignment);
        }
        rejects("DELETE FROM uteexpress.orders");
        assertUnchanged(order);
    }

    @Test void enforcesCheckoutAndPaymentUniquenessWithPaidOnlyPartialIndex() {
        Order first = create();
        Order second = create();
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.orders SET checkout_key=? WHERE id=?",
                first.getCheckoutKey(), second.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.orders SET order_code=? WHERE id=?",
                first.getOrderCode(), second.getId())).isInstanceOf(DataIntegrityViolationException.class);
        Payment a = payments.saveAndFlush(new Payment(first.getId(), CheckoutRequest.PaymentMethod.ONLINE, quote().totals(), "a", NOW));
        Payment b = payments.saveAndFlush(new Payment(first.getId(), CheckoutRequest.PaymentMethod.ONLINE, quote().totals(), "b", NOW));
        Payment c = payments.saveAndFlush(new Payment(second.getId(), CheckoutRequest.PaymentMethod.COD, quote().totals(), "c", NOW));
        assertThat(payments.count()).isEqualTo(3); // multiple UNPAID and null provider references allowed
        rejects("UPDATE uteexpress.payments SET attempt_key='duplicate'");
        rejects("UPDATE uteexpress.payments SET provider_reference='duplicate'");
        jdbc.update("UPDATE uteexpress.payments SET status='PAID', paid_at=? WHERE id=?", java.sql.Timestamp.from(NOW), a.getId());
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.payments SET status='PAID' WHERE id=?", b.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE uteexpress.payments SET status='PAID' WHERE id=?", c.getId());
        assertThat(payments.findById(b.getId()).orElseThrow().getStatus().name()).isEqualTo("UNPAID");
    }

    void rejects(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).as(sql).isInstanceOf(DataIntegrityViolationException.class);
    }
}
