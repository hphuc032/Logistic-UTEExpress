package com.uteexpress.payment.service;

import com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod;
import com.uteexpress.checkout.dto.Money;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.CodCollectionCommand;
import com.uteexpress.payment.dto.PaymentRecordView;
import com.uteexpress.payment.entity.Payment;
import com.uteexpress.payment.repository.PaymentRepository;
import com.uteexpress.payment.repository.PaymentRepository.CodOrderFacts;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PAY-01 payment authority. Internal mutations require the caller's transaction, not a caller-supplied identity. */
@Service
public class CodPaymentService implements PaymentService {
    private final PaymentRepository payments;
    private final PaymentReadService reads;
    private final CurrentAccountIdProvider accounts;
    private final Clock clock;
    private final EntityManager entityManager;

    public CodPaymentService(PaymentRepository payments, PaymentReadService reads,
            CurrentAccountIdProvider accounts, Clock clock, EntityManager entityManager) {
        this.payments = payments;
        this.reads = reads;
        this.accounts = accounts;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentRecordView initializeCodForNewOrder(Long orderId) {
        requireOrderId(orderId);
        var order = lockedOrder(orderId);
        if (!"NEW".equals(order.getStatus()) || !payments.lockRecordsForOrder(orderId).isEmpty()) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        var payment = new Payment(orderId, PaymentMethod.COD, totals(order), "cod-order-" + orderId,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        return view(payments.saveAndFlush(payment));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentRecordView collectCod(CodCollectionCommand command) {
        if (command == null) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        requireOrderId(command.orderId());
        var collected = Money.requireAmount(command.collectedAmount());
        var order = lockedOrder(command.orderId());
        // COD is a prerequisite for SHIPPING -> DELIVERED in the caller's atomic fulfillment transaction.
        if (!"SHIPPING".equals(order.getStatus())) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        var records = payments.lockRecordsForOrder(command.orderId());
        // No guessing the method of a historical order or selecting among ambiguous payment attempts.
        if (records.size() != 1) throw new ApplicationException(ErrorCode.CONFLICT);
        var payment = records.getFirst();
        // A fulfillment caller may already have loaded this entity before acquiring the order lock.
        // Refresh under the lock so an old persistence-context snapshot cannot collect twice.
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        payment.collectCod(totals(order).grandTotal(), collected, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return view(payments.saveAndFlush(payment));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
            + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PaymentRecordView> recordsForBuyer(Long orderId) {
        Long buyer = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        if (orderId == null || orderId <= 0 || !payments.buyerOwnsOrder(orderId, buyer)) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return reads.recordsForOrder(orderId);
    }

    private CodOrderFacts lockedOrder(Long orderId) {
        return payments.lockOrderFacts(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static OrderTotals totals(CodOrderFacts order) {
        return new OrderTotals(order.getSubtotal(), order.getDiscountTotal(), order.getShippingFee(), order.getGrandTotal());
    }

    private static void requireOrderId(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }

    private static PaymentRecordView view(Payment payment) {
        return new PaymentRecordView(payment.getMethod(), payment.getStatus(), payment.getAmount(), payment.getCreatedAt());
    }
}
