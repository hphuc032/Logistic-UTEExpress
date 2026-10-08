package com.uteexpress.shipping.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.ShipmentFulfillmentFacts;
import com.uteexpress.shipping.dto.ShipmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal Shipment boundary. Caller must complete the Order lifecycle in this same transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ShipmentFulfillmentService {
    private static final String SHIPPER = "hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())";
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final CurrentAccountIdProvider accounts;
    private final AuditLogService audit;
    private final Clock clock;

    public ShipmentFulfillmentService(ObjectProvider<JdbcTemplate> jdbcProvider,
            CurrentAccountIdProvider accounts, AuditLogService audit, Clock clock) {
        this.jdbcProvider = jdbcProvider;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Locks Order before Shipment, checks live assignment, then state/version. */
    @PreAuthorize(SHIPPER)
    public ShipmentFulfillmentFacts lockAssignedForTransition(Long orderId,
            Long expectedShipmentVersion, ShipmentStatus expectedStatus) {
        requireVersion(expectedShipmentVersion);
        if (expectedStatus == null) throw invalid();
        lockOrder(orderId);
        ShipmentFulfillmentFacts facts = assigned(orderId);
        if (facts.version() != expectedShipmentVersion || facts.status() != expectedStatus) throw conflict();
        return facts;
    }

    /** Reloads evidence from DB; the caller must not accept client-supplied evidence. */
    @PreAuthorize(SHIPPER)
    public ShipmentFulfillmentFacts requireFulfillmentEvidence(Long orderId,
            Long expectedShipmentVersion, ShipmentStatus expectedStatus) {
        ShipmentFulfillmentFacts facts = lockAssignedForTransition(orderId, expectedShipmentVersion, expectedStatus);
        boolean valid = switch (facts.status()) {
            case PICKED_UP -> facts.pickedUpAt() != null && facts.deliveredAt() == null && facts.attemptCount() == 0;
            case SHIPPING -> facts.pickedUpAt() != null && facts.deliveredAt() == null && facts.attemptCount() == 1;
            case DELIVERED -> facts.pickedUpAt() != null && facts.deliveredAt() != null
                    && !facts.deliveredAt().isBefore(facts.pickedUpAt()) && facts.attemptCount() == 1;
            default -> false;
        };
        if (!valid || facts.maxAttempts() != 2) throw conflict();
        return facts;
    }

    @PreAuthorize(SHIPPER)
    public ShipmentFulfillmentFacts recordPickedUp(Long orderId, Long expectedShipmentVersion) {
        return transition(orderId, expectedShipmentVersion, ShipmentStatus.ASSIGNED,
                ShipmentStatus.PICKED_UP, "CONFIRMED");
    }

    @PreAuthorize(SHIPPER)
    public ShipmentFulfillmentFacts recordShipping(Long orderId, Long expectedShipmentVersion) {
        return transition(orderId, expectedShipmentVersion, ShipmentStatus.PICKED_UP,
                ShipmentStatus.SHIPPING, "PICKED_UP");
    }

    /** Provisional until Payment and Order completion both succeed in the caller's transaction. */
    @PreAuthorize(SHIPPER)
    public ShipmentFulfillmentFacts recordDelivered(Long orderId, Long expectedShipmentVersion) {
        return transition(orderId, expectedShipmentVersion, ShipmentStatus.SHIPPING,
                ShipmentStatus.DELIVERED, "SHIPPING");
    }

    /** Invoke from authorized Order cancellation before status changes, in its existing transaction. */
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public void cancelAssignedForVendorOrder(Long orderId) {
        OrderFacts order = lockOrder(orderId);
        long actor = actor();
        if (order.ownerId() != actor) throw missing();
        if (!List.of("NEW", "CONFIRMED").contains(order.status())) throw conflict();
        ShipmentFulfillmentFacts facts = shipment(orderId);
        if (facts == null) return;
        if (facts.status() != ShipmentStatus.ASSIGNED || facts.pickedUpAt() != null) throw conflict();
        update(facts, ShipmentStatus.CANCELLED, clock.instant().truncatedTo(ChronoUnit.MICROS));
        appendAudit(actor, facts, ShipmentStatus.CANCELLED);
    }

    private ShipmentFulfillmentFacts transition(Long orderId, Long expectedVersion,
            ShipmentStatus from, ShipmentStatus to, String orderStatus) {
        requireVersion(expectedVersion);
        OrderFacts order = lockOrder(orderId);
        ShipmentFulfillmentFacts facts = assigned(orderId);
        if (facts.version() != expectedVersion || facts.status() != from
                || !orderStatus.equals(order.status()) || order.readyAt() == null) throw conflict();
        if (from == ShipmentStatus.ASSIGNED && (facts.pickedUpAt() != null || facts.attemptCount() != 0)) throw conflict();
        if (from == ShipmentStatus.PICKED_UP && (facts.pickedUpAt() == null || facts.attemptCount() != 0)) throw conflict();
        if (from == ShipmentStatus.SHIPPING && (facts.pickedUpAt() == null
                || facts.attemptCount() != 1 || facts.deliveredAt() != null)) throw conflict();
        update(facts, to, clock.instant().truncatedTo(ChronoUnit.MICROS));
        appendAudit(actor(), facts, to);
        return assigned(orderId);
    }

    private void update(ShipmentFulfillmentFacts facts, ShipmentStatus to, Instant at) {
        int count = jdbc().update("""
                UPDATE uteexpress.shipments SET status=?, version=version+1, updated_at=?,
                    picked_up_at=CASE WHEN ?='PICKED_UP' THEN ? ELSE picked_up_at END,
                    delivered_at=CASE WHEN ?='DELIVERED' THEN ? ELSE delivered_at END,
                    attempt_count=CASE WHEN ?='SHIPPING' THEN attempt_count+1 ELSE attempt_count END
                WHERE id=? AND version=? AND status=?
                """, to.name(), java.sql.Timestamp.from(at), to.name(), java.sql.Timestamp.from(at),
                to.name(), java.sql.Timestamp.from(at), to.name(), facts.shipmentId(), facts.version(), facts.status().name());
        if (count != 1) throw conflict();
    }

    private void appendAudit(long actor, ShipmentFulfillmentFacts facts, ShipmentStatus to) {
        audit.append(new AuditEntry(actor, "SHIPMENT_" + to.name(), "SHIPMENT", facts.shipmentId(),
                Map.of("status", facts.status().name(), "version", Long.toString(facts.version())),
                Map.of("status", to.name(), "version", Long.toString(facts.version() + 1)), "SHIPMENT_FULFILLMENT"));
    }

    private ShipmentFulfillmentFacts assigned(Long orderId) {
        ShipmentFulfillmentFacts facts = shipment(orderId);
        if (facts == null || !facts.shipperId().equals(actor())) throw missing();
        Boolean active = jdbc().queryForObject("""
                SELECT EXISTS(SELECT 1 FROM uteexpress.users u JOIN uteexpress.user_roles ur ON ur.user_id=u.id
                    JOIN uteexpress.roles r ON r.id=ur.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code='SHIPPER')
                """, Boolean.class, actor());
        if (!Boolean.TRUE.equals(active)) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        return facts;
    }

    private ShipmentFulfillmentFacts shipment(Long orderId) {
        List<ShipmentFulfillmentFacts> rows = jdbc().query("""
                SELECT id, order_id, assigned_shipper_id, status, version, attempt_count,
                    max_attempts, picked_up_at, delivered_at FROM uteexpress.shipments WHERE order_id=? FOR UPDATE
                """, (rs, ignored) -> new ShipmentFulfillmentFacts(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                ShipmentStatus.valueOf(rs.getString(4)), rs.getLong(5), rs.getInt(6), rs.getInt(7),
                rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(),
                rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant()), orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private OrderFacts lockOrder(Long orderId) {
        if (orderId == null || orderId <= 0) throw invalid();
        List<OrderFacts> rows = jdbc().query("""
                SELECT o.status, o.ready_at, s.owner_id FROM uteexpress.orders o
                JOIN uteexpress.shops s ON s.id=o.shop_id WHERE o.id=? FOR UPDATE OF o
                """, (rs, ignored) -> new OrderFacts(rs.getString(1), rs.getTimestamp(2), rs.getLong(3)), orderId);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    private long actor() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }
    private JdbcTemplate jdbc() { return jdbcProvider.getObject(); }
    private static void requireVersion(Long version) { if (version == null || version < 0) throw invalid(); }
    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.VALIDATION_FAILED); }
    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
    private static ApplicationException missing() { return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND); }
    private record OrderFacts(String status, java.sql.Timestamp readyAt, long ownerId) { }
}
