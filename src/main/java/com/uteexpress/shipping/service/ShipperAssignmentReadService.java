package com.uteexpress.shipping.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.ShipmentAssignment;
import com.uteexpress.shipping.dto.ShipmentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only SHIP-02 entry point. Fulfillment writes require the Order lifecycle boundary. */
@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
public class ShipperAssignmentReadService {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final CurrentAccountIdProvider currentAccount;

    public ShipperAssignmentReadService(ObjectProvider<JdbcTemplate> jdbcProvider,
            CurrentAccountIdProvider currentAccount) {
        this.jdbcProvider = jdbcProvider;
        this.currentAccount = currentAccount;
    }

    @Transactional(readOnly = true)
    public List<ShipmentAssignment> assigned() {
        return jdbc().query("""
                SELECT id, order_id, provider_id, shipping_service_snapshot, fee_snapshot,
                    assigned_shipper_id, status, version FROM uteexpress.shipments
                WHERE assigned_shipper_id = ? ORDER BY id DESC
                """, ShipperAssignmentReadService::map, actor());
    }

    @Transactional(readOnly = true)
    public ShipmentAssignment detail(Long shipmentId) {
        if (shipmentId == null || shipmentId <= 0)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        List<ShipmentAssignment> rows = jdbc().query("""
                SELECT id, order_id, provider_id, shipping_service_snapshot, fee_snapshot,
                    assigned_shipper_id, status, version FROM uteexpress.shipments
                WHERE id = ? AND assigned_shipper_id = ?
                """, ShipperAssignmentReadService::map, shipmentId, actor());
        if (rows.isEmpty()) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public Long orderVersion(Long shipmentId) {
        if (shipmentId == null || shipmentId <= 0)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        List<Long> versions = jdbc().query("""
                SELECT o.version FROM uteexpress.orders o JOIN uteexpress.shipments s ON s.order_id=o.id
                WHERE s.id=? AND s.assigned_shipper_id=?
                """, (rs, ignored) -> rs.getLong(1), shipmentId, actor());
        if (versions.isEmpty()) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        return versions.getFirst();
    }

    private long actor() {
        return currentAccount.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private JdbcTemplate jdbc() { return jdbcProvider.getObject(); }

    private static ShipmentAssignment map(ResultSet rs, int ignored) throws SQLException {
        return new ShipmentAssignment(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getBigDecimal(5), rs.getLong(6), ShipmentStatus.valueOf(rs.getString(7)), rs.getLong(8));
    }
}
