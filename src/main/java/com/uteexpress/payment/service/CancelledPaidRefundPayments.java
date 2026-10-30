package com.uteexpress.payment.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.common.money.Money;
import com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod;
import com.uteexpress.payment.dto.*;
import com.uteexpress.payment.repository.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CancelledPaidRefundPayments {
    private final ObjectProvider<PaymentRepository> payments;
    private final RefundLedgerRepository refunds;
    private final ObjectProvider<EntityManager> entities;
    public CancelledPaidRefundPayments(ObjectProvider<PaymentRepository> payments,RefundLedgerRepository refunds,ObjectProvider<EntityManager> entities) {
        this.payments=payments; this.refunds=refunds; this.entities=entities;
    }
    public void lockKey(CancelledPaidRefundPayload p) { refunds.lockKey(p.advisoryLockId()); }
    public boolean hasKey(CancelledPaidRefundPayload p) { return refunds.byKey(p.OPERATION,p.command().idempotencyKey()).isPresent(); }
    public Optional<RefundResult> replay(CancelledPaidRefundPayload p,Long actor) {
        return refunds.byKey(p.OPERATION,p.command().idempotencyKey()).map(r->{
            p.requireReplayMatch(r.operation(),r.key(),r.canonical(),r.sha256(),r.processedBy(),actor);
            return r.result(true);
        });
    }
    public CollectedPaidRefund lockPaid(Long orderId,BigDecimal total,Instant cancelledAt) {
        var records=payments.getObject().lockRecordsForOrder(orderId);
        records.forEach(p->entities.getObject().refresh(p,LockModeType.PESSIMISTIC_WRITE));
        var successes=records.stream().filter(p->p.getStatus()==PaymentStatus.PAID).toList();
        if (successes.size()!=1 || total==null || total.signum()<=0 || cancelledAt==null) throw conflict();
        var p=successes.getFirst();
        try { Money.requireAmount(total); Money.requireAmount(p.getAmount()); }
        catch (ApplicationException badMoney) { throw conflict(); }
        if (p.getId()==null || !orderId.equals(p.getOrderId()) || p.getMethod()==null
                || p.getPaidAt()==null || p.getCreatedAt()==null || p.getPaidAt().isBefore(p.getCreatedAt())
                || p.getPaidAt().isAfter(cancelledAt) || p.getExpiredAt()!=null
                || p.getAmount().compareTo(total)!=0
                || p.getMethod()==PaymentMethod.ONLINE && (p.getProviderReference()==null || p.getProviderReference().isBlank())
                || refunds.lockForOrder(orderId)) throw conflict();
        return new CollectedPaidRefund(p.getId(),orderId,total,p.getPaidAt());
    }
    public RefundResult record(CancelledPaidRefundPayload p,CollectedPaidRefund paid,Long actor,
            Instant cancelledAt,Instant at) {
        if (!p.command().orderId().equals(paid.orderId()) || cancelledAt.isAfter(at)) throw conflict();
        return refunds.insertCancelledPaid(paid.orderId(),paid.paymentId(),actor,p.OPERATION,
                p.command().idempotencyKey(),p.SCHEMA_VERSION,p.command().expectedOrderVersion(),
                p.canonical(),p.sha256(),paid.amount(),p.command().proofReference(),at,paid.paidAt(),cancelledAt).result(false);
    }
    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
}
