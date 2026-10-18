package com.uteexpress.order.entity;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.ReturnRequestStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "return_requests", uniqueConstraints =
        @UniqueConstraint(name = "uq_return_requests_order_id", columnNames = "order_id"))
public class ReturnRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false, updatable = false) private Long orderId;
    @Column(name = "requested_by", nullable = false, updatable = false) private Long requestedBy;
    @Column(name = "reason", nullable = false, length = 1000, updatable = false) private String reason;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    private ReturnRequestStatus status;
    @Column(name = "requested_at", nullable = false, updatable = false) private Instant requestedAt;
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decision_at") private Instant decisionAt;
    @Column(name = "decision_note", length = 1000) private String decisionNote;
    @Column(name = "received_at") private Instant receivedAt;
    @Column(name = "received_by") private Long receivedBy;
    @Column(name = "receipt_reference", length = 255) private String receiptReference;
    @Column(name = "restockable") private Boolean restockable;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private Long version;

    protected ReturnRequest() { }

    public ReturnRequest(Long orderId, Long requestedBy, String reason, Instant at) {
        if (orderId == null || orderId <= 0 || requestedBy == null || requestedBy <= 0)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        this.orderId = orderId;
        this.requestedBy = requestedBy;
        this.reason = requireReason(reason);
        this.status = ReturnRequestStatus.PENDING;
        this.requestedAt = Objects.requireNonNull(at);
        this.createdAt = at;
        this.updatedAt = at;
    }

    public void decide(boolean approve, Long actor, String note, Instant at) {
        if (status != ReturnRequestStatus.PENDING) throw new ApplicationException(ErrorCode.CONFLICT);
        if (actor == null || actor <= 0 || at == null || at.isBefore(requestedAt))
            throw new ApplicationException(ErrorCode.CONFLICT);
        if (approve && note != null) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        decisionNote = approve ? null : requireReason(note);
        status = approve ? ReturnRequestStatus.APPROVED : ReturnRequestStatus.REJECTED;
        decidedBy = actor;
        decisionAt = at;
        updatedAt = at;
    }

    public void receive(Long actor, String reference, Instant at) {
        if (status != ReturnRequestStatus.APPROVED || receivedAt != null || receivedBy != null
                || receiptReference != null || actor == null || actor <= 0 || at == null
                || decisionAt == null || at.isBefore(decisionAt))
            throw new ApplicationException(ErrorCode.CONFLICT);
        receiptReference = requireReceiptReference(reference);
        receivedBy = actor;
        receivedAt = at;
        updatedAt = at;
    }

    public static String requireReceiptReference(String value) {
        if (value == null || value.isBlank() || value.length() > 255)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        return value.strip();
    }

    public static String requireReason(String value) {
        if (value == null || value.isBlank() || value.length() > 1000)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        return value.strip();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getRequestedBy() { return requestedBy; }
    public String getReason() { return reason; }
    public ReturnRequestStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Long getDecidedBy() { return decidedBy; }
    public Instant getDecisionAt() { return decisionAt; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getReceivedAt() { return receivedAt; }
    public Long getReceivedBy() { return receivedBy; }
    public String getReceiptReference() { return receiptReference; }
    public Boolean getRestockable() { return restockable; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
