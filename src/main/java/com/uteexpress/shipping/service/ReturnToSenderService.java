package com.uteexpress.shipping.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.shipping.dto.ReturnToSenderReceipt;
import com.uteexpress.shipping.dto.ShipmentFulfillmentFacts;
import com.uteexpress.shipping.dto.ShipmentStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal Ops receipt authority. Lifecycle caller owns Order first and commits all effects together. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
public class ReturnToSenderService {
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final ShipmentFulfillmentService shipments;
    private final AuditLogService audit;

    public ReturnToSenderService(ObjectProvider<JdbcTemplate> jdbc, ShipmentFulfillmentService shipments, AuditLogService audit) {
        this.jdbc = jdbc; this.shipments = shipments; this.audit = audit;
    }

    public ShipmentFulfillmentFacts lock(Long orderId) {
        return shipments.lockForOpsCancellation(orderId).orElseThrow(() -> new ApplicationException(ErrorCode.CONFLICT));
    }

    public Optional<ReturnToSenderReceipt> receipt(Long orderId) {
        return jdbc.getObject().query("""
                SELECT order_id,shipment_id,received_by,original_order_version,original_shipment_version,receipt_reference,received_at
                FROM uteexpress.shipment_return_receipts WHERE order_id=?
                """, (rs, n) -> new ReturnToSenderReceipt(rs.getLong(1),rs.getLong(2),rs.getLong(3),
                        rs.getLong(4),rs.getLong(5),rs.getString(6),rs.getTimestamp(7).toInstant()), orderId).stream().findFirst();
    }

    public void receive(ShipmentFulfillmentFacts facts, long originalOrderVersion, long actor, String reference, Instant at) {
        if (facts.status() != ShipmentStatus.DELIVERY_FAILED || facts.attemptCount() != 2 || facts.maxAttempts() != 2
                || facts.pickedUpAt() == null || facts.deliveredAt() != null) throw conflict();
        var failures = jdbc.getObject().queryForList("""
                SELECT attempt_number,source_shipment_version,shipper_id,recorded_at
                FROM uteexpress.shipment_delivery_failures WHERE shipment_id=? ORDER BY attempt_number
                """, facts.shipmentId());
        if (failures.size() != 2) throw conflict();
        var first = failures.getFirst(); var second = failures.getLast();
        Instant a = ((Timestamp)first.get("recorded_at")).toInstant(), b = ((Timestamp)second.get("recorded_at")).toInstant();
        if (((Number)first.get("attempt_number")).intValue() != 1 || ((Number)second.get("attempt_number")).intValue() != 2
                || ((Number)first.get("shipper_id")).longValue() != facts.shipperId()
                || ((Number)second.get("shipper_id")).longValue() != facts.shipperId()
                || ((Number)second.get("source_shipment_version")).longValue() != facts.version()-1
                || ((Number)first.get("source_shipment_version")).longValue() != facts.version()-3
                || a.isBefore(facts.pickedUpAt()) || b.isBefore(a) || at.isBefore(b)) throw conflict();
        jdbc.getObject().update("""
                INSERT INTO uteexpress.shipment_return_receipts(shipment_id,order_id,received_by,original_order_version,
                    original_shipment_version,receipt_reference,received_at) VALUES (?,?,?,?,?,?,?)
                """, facts.shipmentId(),facts.orderId(),actor,originalOrderVersion,facts.version(),reference,Timestamp.from(at));
        int changed = jdbc.getObject().update("""
                UPDATE uteexpress.shipments SET status='RETURNED_TO_SENDER',version=version+1,updated_at=?
                WHERE id=? AND version=? AND status='DELIVERY_FAILED'
                """, Timestamp.from(at),facts.shipmentId(),facts.version());
        if (changed != 1) throw conflict();
        audit.append(new AuditEntry(actor,"SHIPMENT_RETURNED_TO_SENDER","SHIPMENT",facts.shipmentId(),
                Map.of("status","DELIVERY_FAILED"),Map.of("status","RETURNED_TO_SENDER"),"PHYSICAL_RETURN_ATTESTED"));
    }

    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
}
