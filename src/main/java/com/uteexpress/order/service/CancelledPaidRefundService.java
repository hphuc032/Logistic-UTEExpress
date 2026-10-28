package com.uteexpress.order.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.payment.dto.*;
import com.uteexpress.payment.service.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Financial completion without an OrderStatus write: CANCELLED remains terminal. */
@Service
public class CancelledPaidRefundService {
    private final RefundOrderAuthority authority;
    private final CancelledPaidRefundPayments payments;
    private final ObjectProvider<PlatformTransactionManager> managers;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public CancelledPaidRefundService(RefundOrderAuthority authority,CancelledPaidRefundPayments payments,
            ObjectProvider<PlatformTransactionManager> managers,Clock clock,ApplicationEventPublisher events) {
        this.authority=authority;this.payments=payments;this.managers=managers;this.clock=clock;this.events=events;
    }
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
    public RefundResult complete(CancelledPaidRefundCommand command) {
        var payload=CancelledPaidRefundPayload.from(command);
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        // Match existing persistence-free HTTP fixtures; a mutation still requires a real manager.
        var tx=new TransactionTemplate(managers.getObject());tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            return tx.execute(status->{
                payments.lockKey(payload);
                if (payments.hasKey(payload)) {
                    Long actor=authority.authorizeAdmin();
                    return payments.replay(payload,actor).orElseThrow(()->new ApplicationException(ErrorCode.CONFLICT));
                }
                var locked=authority.lock(command.orderId());
                var paid=payments.lockPaid(locked.order().getId(),locked.order().getGrandTotal(),locked.order().getCancelledAt());
                Long actor=authority.authorizeAdmin();var at=clock.instant().truncatedTo(ChronoUnit.MICROS);
                authority.requireCancelledPaidEligible(locked,payload.command(),paid.paidAt(),at);
                var result=payments.record(payload,paid,actor,locked.order().getCancelledAt(),at);
                authority.auditCancelledPaid(command.orderId(),command.expectedOrderVersion(),actor,result.refundId());
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        events.publishEvent(new CancelledPaidRefundRecordedEvent(result.refundId(),result.orderId(),
                                actor,result.refundAmount(),result.serverTimestamp()));
                    }
                });
                return result;
            });
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException | jakarta.persistence.OptimisticLockException ex) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }
}
