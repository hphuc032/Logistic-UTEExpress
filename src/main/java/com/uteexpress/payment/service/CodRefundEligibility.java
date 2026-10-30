package com.uteexpress.payment.service;

import com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.money.Money;
import com.uteexpress.payment.dto.PaymentStatus;
import com.uteexpress.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Payment-internal pure guard. The MANDATORY refund authority locks/reloads these facts first.
 * Does not establish Order/receipt eligibility, ADMIN authorization, persistence or idempotency.
 */
final class CodRefundEligibility {
    private CodRefundEligibility() { }

    record CollectedCod(Long paymentId, Long orderId, BigDecimal amount, Instant paidAt) { }

    static CollectedCod requireCollectedCod(List<Payment> records, Long orderId, BigDecimal persistedGrandTotal) {
        if (orderId == null || orderId <= 0 || records == null || records.size() != 1) throw conflict();
        BigDecimal total = positiveWholeVnd(persistedGrandTotal);
        Payment payment = records.getFirst();
        if (payment == null || payment.getId() == null || payment.getId() <= 0
                || !orderId.equals(payment.getOrderId()) || payment.getMethod() != PaymentMethod.COD
                || payment.getStatus() != PaymentStatus.PAID || payment.getPaidAt() == null
                || payment.getExpiredAt() != null || positiveWholeVnd(payment.getAmount()).compareTo(total) != 0) {
            throw conflict();
        }
        return new CollectedCod(payment.getId(), orderId, total, payment.getPaidAt());
    }

    private static BigDecimal positiveWholeVnd(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw conflict();
        try {
            return Money.requireAmount(amount);
        } catch (ApplicationException invalidPersistedMoney) {
            throw conflict();
        }
    }

    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
}
