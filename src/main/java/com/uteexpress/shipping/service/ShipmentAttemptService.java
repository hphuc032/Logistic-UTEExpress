package com.uteexpress.shipping.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.shipping.dto.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal Shipping authority; coordinator must call TD prepare/verify in this transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
public class ShipmentAttemptService {
    private final ShipmentFulfillmentService fulfillment;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final AuditLogService audit;
    private final Clock clock;
    public ShipmentAttemptService(ShipmentFulfillmentService fulfillment, ObjectProvider<JdbcTemplate> jdbc,
            AuditLogService audit, Clock clock) {
        this.fulfillment = fulfillment; this.jdbc = jdbc; this.audit = audit; this.clock = clock;
    }

    /** TD calls this after locking Order; no caller-created facts are accepted. */
    public ShipmentDeliveryFacts lockForOperation(Long orderId, Long version, DeliveryAttemptOperation operation) {
        if (operation == null) throw invalid();
        return switch (operation) {
            case REPORT_DELIVERY_FAILURE -> lockForReportFailure(orderId, version);
            case RETRY_DELIVERY -> lockForRetryDelivery(orderId, version);
        };
    }

    /** The version is always the original source token; reload target and immutable evidence. */
    public ShipmentDeliveryFacts requireOperationEvidence(Long orderId, Long version, DeliveryAttemptOperation operation) {
        if (operation == null) throw invalid();
        return switch (operation) {
            case REPORT_DELIVERY_FAILURE -> requireFailureEvidence(orderId, version);
            case RETRY_DELIVERY -> requireRetryEvidence(orderId, version);
        };
    }
    public ShipmentDeliveryFacts lockForReportFailure(Long orderId, Long version) {
        requireVersion(version);
        var facts = lock(orderId, version, ShipmentStatus.SHIPPING);
        if (failure(facts.shipmentId(), facts.attemptCount()) != null) throw conflict();
        if (facts.attemptCount() == 2) requireFirstFailure(facts, version - 2);
        return new ShipmentDeliveryFacts(facts, null);
    }

    public ShipmentDeliveryFacts lockForRetryDelivery(Long orderId, Long version) {
        requireVersion(version);
        var facts = lock(orderId, version, ShipmentStatus.DELIVERY_FAILED);
        if (facts.attemptCount() != 1) throw conflict();
        return new ShipmentDeliveryFacts(facts, requireEvidence(facts, 1, version - 1));
    }

    public void reportFailure(Long orderId, Long version, String reason, String reference) {
        String normalizedReason = text(reason, 1000), normalizedReference = text(reference, 255);
        var facts = lockForReportFailure(orderId, version).shipment();
        var at = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (at.isBefore(facts.pickedUpAt())) throw conflict();
        try {
            jdbc().update("""
                    INSERT INTO uteexpress.shipment_delivery_failures
                      (shipment_id,attempt_number,source_shipment_version,shipper_id,failure_reason,evidence_reference,recorded_at)
                    VALUES (?,?,?,?,?,?,?)
                    """, facts.shipmentId(), facts.attemptCount(), version, facts.shipperId(),
                    normalizedReason, normalizedReference, Timestamp.from(at));
        } catch (DuplicateKeyException duplicate) { throw conflict(); }
        mutate(facts, ShipmentStatus.DELIVERY_FAILED, facts.attemptCount(), at);
        recordAudit(facts, ShipmentStatus.DELIVERY_FAILED, "DELIVERY_FAILURE_REPORTED");
    }

    public void retryDelivery(Long orderId, Long version) {
        var facts = lockForRetryDelivery(orderId, version).shipment();
        var at = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var evidence = requireEvidence(facts, 1, version - 1);
        if (at.isBefore(evidence.recordedAt())) throw conflict();
        mutate(facts, ShipmentStatus.SHIPPING, 2, at);
        recordAudit(facts, ShipmentStatus.SHIPPING, "DELIVERY_RETRIED");
    }

    public ShipmentDeliveryFacts requireFailureEvidence(Long orderId, Long originalVersion) {
        requireVersion(originalVersion);
        var facts = lock(orderId, originalVersion + 1, ShipmentStatus.DELIVERY_FAILED);
        var evidence = requireEvidence(facts, facts.attemptCount(), originalVersion);
        if (facts.attemptCount() == 2) requireFirstFailure(facts, originalVersion - 2);
        return new ShipmentDeliveryFacts(facts, evidence);
    }

    public ShipmentDeliveryFacts requireRetryEvidence(Long orderId, Long originalVersion) {
        requireVersion(originalVersion);
        var facts = lock(orderId, originalVersion + 1, ShipmentStatus.SHIPPING);
        if (facts.attemptCount() != 2 || failure(facts.shipmentId(), 2) != null) throw conflict();
        return new ShipmentDeliveryFacts(facts, requireEvidence(facts, 1, originalVersion - 1));
    }

    private ShipmentFulfillmentFacts lock(Long orderId, Long version, ShipmentStatus status) {
        // Target evidence may legitimately be at MAX_VALUE; mutation source may not overflow.
        if (version == null || version < 0) throw invalid();
        var facts = fulfillment.lockAssignedForTransition(orderId, version, status);
        var order = jdbc().queryForMap("SELECT status,ready_at FROM uteexpress.orders WHERE id=?", orderId);
        if (!"SHIPPING".equals(order.get("status")) || order.get("ready_at") == null
                || facts.maxAttempts() != 2 || facts.attemptCount() < 1 || facts.attemptCount() > 2
                || facts.pickedUpAt() == null || facts.deliveredAt() != null) throw conflict();
        return facts;
    }

    private void requireFirstFailure(ShipmentFulfillmentFacts facts, long sourceVersion) {
        requireEvidence(facts, 1, sourceVersion);
    }
    private DeliveryFailureEvidence requireEvidence(ShipmentFulfillmentFacts facts, int attempt, long sourceVersion) {
        var evidence = failure(facts.shipmentId(), attempt);
        if (evidence == null || evidence.sourceShipmentVersion() != sourceVersion
                || !evidence.shipperId().equals(facts.shipperId()) || evidence.recordedAt().isBefore(facts.pickedUpAt()))
            throw conflict();
        return evidence;
    }
    private DeliveryFailureEvidence failure(Long shipmentId, int attempt) {
        var rows = jdbc().query("""
                SELECT id,shipment_id,attempt_number,source_shipment_version,shipper_id,
                    failure_reason,evidence_reference,recorded_at
                FROM uteexpress.shipment_delivery_failures WHERE shipment_id=? AND attempt_number=?
                """, (rs, i) -> new DeliveryFailureEvidence(rs.getLong(1),rs.getLong(2),rs.getInt(3),
                rs.getLong(4),rs.getLong(5),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()), shipmentId,attempt);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private void mutate(ShipmentFulfillmentFacts facts, ShipmentStatus status, int attempt, java.time.Instant at) {
        requireVersion(facts.version());
        int updated = jdbc().update("""
                UPDATE uteexpress.shipments SET status=?,attempt_count=?,version=version+1,updated_at=?
                WHERE id=? AND status=? AND version=?
                """, status.name(),attempt,Timestamp.from(at),facts.shipmentId(),facts.status().name(),facts.version());
        if (updated != 1) throw conflict();
    }
    private void recordAudit(ShipmentFulfillmentFacts facts, ShipmentStatus to, String code) {
        audit.append(new AuditEntry(facts.shipperId(),code,"SHIPMENT",facts.shipmentId(),
                Map.of("status",facts.status().name(),"version",Long.toString(facts.version())),
                Map.of("status",to.name(),"version",Long.toString(facts.version()+1)),code));
    }
    private JdbcTemplate jdbc() { return jdbc.getObject(); }
    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw invalid();
        return value.strip();
    }
    private static void requireVersion(Long version) {
        if (version == null || version < 0 || version == Long.MAX_VALUE) throw invalid();
    }
    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.VALIDATION_FAILED); }
    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
}
