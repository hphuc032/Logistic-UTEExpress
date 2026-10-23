package com.uteexpress.payment.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.payment.dto.*;
import com.uteexpress.payment.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Payment-owned evidence and append-only ledger. Lifecycle supplies locked Order/receipt facts. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ManualCodRefundService {
    private final PaymentRepository payments;
    private final RefundLedgerRepository refunds;
    private final EntityManager entities;
    public ManualCodRefundService(PaymentRepository payments, RefundLedgerRepository refunds, EntityManager entities) {
        this.payments = payments; this.refunds = refunds; this.entities = entities;
    }
    public void lockKey(ManualRefundPayload payload) { refunds.lockKey(payload.advisoryLockId()); }

    /** Caller has authorized and retained current ADMIN Account before invoking this replay read. */
    public Optional<RefundResult> replay(ManualRefundPayload payload, Long currentAdmin) {
        return refunds.byKey(ManualRefundPayload.OPERATION, payload.command().idempotencyKey()).map(row -> {
            payload.requireReplayMatch(row.operation(), row.key(), row.canonical(), row.sha256(), row.processedBy(), currentAdmin);
            return row.result(true);
        });
    }
    public boolean hasKey(ManualRefundPayload payload) {
        return refunds.byKey(ManualRefundPayload.OPERATION, payload.command().idempotencyKey()).isPresent();
    }
    public CollectedCodRefund lockCollectedCod(Long orderId, BigDecimal grandTotal) {
        var records = payments.lockRecordsForOrder(orderId);
        records.forEach(p -> entities.refresh(p, LockModeType.PESSIMISTIC_WRITE));
        var cod = CodRefundEligibility.requireCollectedCod(records, orderId, grandTotal);
        if (refunds.lockForOrder(orderId)) throw new ApplicationException(ErrorCode.CONFLICT);
        return new CollectedCodRefund(cod.paymentId(), cod.orderId(), cod.amount(), cod.paidAt());
    }
    public RefundResult record(ManualRefundPayload payload, CollectedCodRefund cod, Long requestId,
            Long actor, Instant receivedAt, Instant at) {
        var c = payload.command();
        if (!c.orderId().equals(cod.orderId()) || cod.paidAt().isAfter(at) || receivedAt.isAfter(at))
            throw new ApplicationException(ErrorCode.CONFLICT);
        return refunds.insert(c.orderId(), cod.paymentId(), requestId, actor, ManualRefundPayload.OPERATION,
                c.idempotencyKey(), ManualRefundPayload.SCHEMA_VERSION, c.expectedOrderVersion(),
                c.expectedReturnRequestVersion(), payload.canonical(), payload.sha256(), cod.amount(),
                c.proofReference(), at, cod.paidAt(), receivedAt).result(false);
    }
}
