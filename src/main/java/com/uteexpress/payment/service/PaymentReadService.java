package com.uteexpress.payment.service;

import com.uteexpress.payment.dto.PaymentRecordView;
import com.uteexpress.payment.repository.PaymentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal read boundary. The caller must first authorize ownership of the order. */
@Service
public class PaymentReadService {
    private final PaymentRepository payments;
    private final jakarta.persistence.EntityManager entityManager;

    public PaymentReadService(PaymentRepository payments, jakarta.persistence.EntityManager entityManager) {
        this.payments = payments;
        this.entityManager = entityManager;
    }

    /** ORD-05 delivery guard. Caller already holds Order then Shipment; locks and refreshes Payment evidence. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireCollectedCodForDelivery(Long authorizedOrderId, java.math.BigDecimal total) {
        var records = payments.lockRecordsForOrder(authorizedOrderId);
        records.forEach(payment -> entityManager.refresh(payment, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        if (records.size() != 1 || total == null) throw new com.uteexpress.common.exception.ApplicationException(
                com.uteexpress.common.exception.ErrorCode.CONFLICT);
        var payment = records.getFirst();
        if (payment.getMethod() != com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod.COD
                || payment.getStatus() != com.uteexpress.payment.dto.PaymentStatus.PAID
                || payment.getAmount().compareTo(total) != 0
                || payment.getPaidAt() == null || payment.getExpiredAt() != null) {
            throw new com.uteexpress.common.exception.ApplicationException(com.uteexpress.common.exception.ErrorCode.CONFLICT);
        }
    }

    /** ORD-03 guard only. Caller already holds the Order lock; payment facts are never mutated. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireConfirmablePayment(Long authorizedOrderId, java.math.BigDecimal total) {
        var records = payments.lockRecordsForOrder(authorizedOrderId);
        records.forEach(payment -> entityManager.refresh(payment, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        boolean valid;
        if (records.size() == 1
                && records.getFirst().getMethod() == com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod.COD) {
            var payment = records.getFirst();
            valid = payment.getAmount().compareTo(total) == 0 && payment.getExpiredAt() == null
                    && payment.getStatus() == com.uteexpress.payment.dto.PaymentStatus.UNPAID && payment.getPaidAt() == null;
        } else {
            // ONLINE permits failed/pending attempts alongside its one successful payment.
            // Mixed COD/ONLINE evidence remains ambiguous; never infer the order's payment method.
            var paid = records.stream().filter(payment -> payment.getStatus() == com.uteexpress.payment.dto.PaymentStatus.PAID).toList();
            valid = records.stream().allMatch(payment -> payment.getMethod() == com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod.ONLINE)
                    && paid.size() == 1 && paid.getFirst().getAmount().compareTo(total) == 0
                    && paid.getFirst().getPaidAt() != null && paid.getFirst().getExpiredAt() == null;
        }
        if (!valid) throw new com.uteexpress.common.exception.ApplicationException(
                com.uteexpress.common.exception.ErrorCode.CONFLICT);
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordView> recordsForOrder(Long authorizedOrderId) {
        return payments.findByOrderIdOrderByCreatedAtAscIdAsc(authorizedOrderId).stream()
                .map(payment -> new PaymentRecordView(payment.getMethod(), payment.getStatus(),
                        payment.getAmount(), payment.getCreatedAt())).toList();
    }
}
