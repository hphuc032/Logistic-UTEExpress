package com.uteexpress.shipping.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.governance.service.RoleGovernanceService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.ShipmentAssignment;
import com.uteexpress.shipping.dto.ShipmentStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assignment owns Shipment facts only; Order status remains with the Order lifecycle authority. */
@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), "
        + "T(com.uteexpress.security.RoleCode).MANAGER.authority())")
public class ShipmentAssignmentService {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final RoleGovernanceService roles;
    private final CurrentAccountIdProvider currentAccount;
    private final AuditLogService audit;

    public ShipmentAssignmentService(ObjectProvider<JdbcTemplate> jdbcProvider,
            RoleGovernanceService roles, CurrentAccountIdProvider currentAccount, AuditLogService audit) {
        this.jdbcProvider = jdbcProvider;
        this.roles = roles;
        this.currentAccount = currentAccount;
        this.audit = audit;
    }

    @Transactional
    public ShipmentAssignment assign(Long orderId, Long shipperId, Long expectedOrderVersion) {
        requireId(orderId);
        requireId(shipperId);
        requireVersion(expectedOrderVersion);
        long actor = actor();
        OrderFacts order = lockOrder(orderId);
        requireAssignable(order);
        if (order.version() != expectedOrderVersion) throw conflict();
        requireActiveShipper(shipperId);
        // A provider can be deactivated after checkout; the persisted checkout facts remain authoritative.
        // The FK still guarantees that its historic provider row exists.
        Long id;
        try {
            id = jdbc().queryForObject("""
                    INSERT INTO uteexpress.shipments (order_id, provider_id, assigned_shipper_id,
                        status, shipping_service_snapshot, fee_snapshot)
                    VALUES (?, ?, ?, 'ASSIGNED', ?, ?) RETURNING id
                    """, Long.class, orderId, order.providerId(), shipperId,
                    order.serviceCode(), order.fee());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw conflict();
        }
        jdbc().update("""
                INSERT INTO uteexpress.shipment_assignment_history
                    (shipment_id, previous_shipper_id, assigned_shipper_id, actor_id)
                VALUES (?, NULL, ?, ?)
                """, id, shipperId, actor);
        audit.append(new AuditEntry(actor, "SHIPMENT_ASSIGNED", "SHIPMENT", id,
                Map.of(), Map.of("status", "ASSIGNED", "relatedId", shipperId.toString()),
                "SHIPMENT_ASSIGNMENT"));
        return load(id);
    }

    @Transactional
    public ShipmentAssignment reassign(Long orderId, Long shipperId, Long expectedShipmentVersion) {
        requireId(orderId);
        requireId(shipperId);
        requireVersion(expectedShipmentVersion);
        long actor = actor();
        OrderFacts order = lockOrder(orderId);
        requireAssignable(order);
        ShipmentAssignment current = lockShipment(orderId);
        if (current == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        if (current.status() != ShipmentStatus.ASSIGNED
                || !current.version().equals(expectedShipmentVersion)
                || current.shipperId().equals(shipperId)) throw conflict();
        requireActiveShipper(shipperId);
        int updated = jdbc().update("""
                UPDATE uteexpress.shipments SET assigned_shipper_id = ?, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ? AND version = ? AND status = 'ASSIGNED'
                """, shipperId, current.id(), expectedShipmentVersion);
        if (updated != 1) throw conflict();
        jdbc().update("""
                INSERT INTO uteexpress.shipment_assignment_history
                    (shipment_id, previous_shipper_id, assigned_shipper_id, actor_id)
                VALUES (?, ?, ?, ?)
                """, current.id(), current.shipperId(), shipperId, actor);
        audit.append(new AuditEntry(actor, "SHIPMENT_REASSIGNED", "SHIPMENT", current.id(),
                Map.of("status", "ASSIGNED", "relatedId", current.shipperId().toString()),
                Map.of("status", "ASSIGNED", "relatedId", shipperId.toString()),
                "SHIPMENT_ASSIGNMENT"));
        return load(current.id());
    }

    private void requireActiveShipper(Long shipperId) {
        roles.requireActiveShipperForUpdate(shipperId);
    }

    private long actor() {
        return currentAccount.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private OrderFacts lockOrder(Long id) {
        List<OrderFacts> rows = jdbc().query("""
                SELECT status, version, ready_at, shipping_provider_id, shipping_service_code,
                    shipping_fee FROM uteexpress.orders WHERE id = ? FOR UPDATE
                """, (rs, ignored) -> new OrderFacts(rs.getString(1), rs.getLong(2),
                rs.getTimestamp(3), (Long) rs.getObject(4), rs.getString(5), rs.getBigDecimal(6)), id);
        if (rows.isEmpty()) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private static void requireAssignable(OrderFacts order) {
        if (!"CONFIRMED".equals(order.status()) || order.readyAt() == null
                || order.providerId() == null || order.providerId() <= 0
                || order.serviceCode() == null || !order.serviceCode().matches("[A-Z][A-Z0-9_]{0,31}"))
            throw conflict();
    }

    private ShipmentAssignment lockShipment(Long orderId) {
        List<ShipmentAssignment> rows = jdbc().query("""
                SELECT id, order_id, provider_id, shipping_service_snapshot, fee_snapshot,
                    assigned_shipper_id, status, version FROM uteexpress.shipments
                WHERE order_id = ? FOR UPDATE
                """, (rs, ignored) -> map(rs), orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ShipmentAssignment load(Long id) {
        return jdbc().queryForObject("""
                SELECT id, order_id, provider_id, shipping_service_snapshot, fee_snapshot,
                    assigned_shipper_id, status, version FROM uteexpress.shipments WHERE id = ?
                """, (rs, ignored) -> map(rs), id);
    }

    private static ShipmentAssignment map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ShipmentAssignment(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getBigDecimal(5), rs.getLong(6), ShipmentStatus.valueOf(rs.getString(7)), rs.getLong(8));
    }

    private JdbcTemplate jdbc() { return jdbcProvider.getObject(); }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }

    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }

    private record OrderFacts(String status, long version, Timestamp readyAt, Long providerId,
            String serviceCode, BigDecimal fee) { }
}
