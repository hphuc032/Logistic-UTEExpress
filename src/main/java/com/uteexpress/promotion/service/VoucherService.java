package com.uteexpress.promotion.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.entity.*;
import com.uteexpress.promotion.repository.*;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

/** Internal checkout boundary; no browser-supplied buyer, eligibility or monetary facts. */
@Service
public class VoucherService {
    private static final Object LOCKED_RESOURCE = VoucherService.class.getName() + ".LOCKED_VOUCHER";
    private final VoucherRepository vouchers;
    private final VoucherUsageRepository usages;
    private final CurrentAccountIdProvider accounts;
    private final EntityManager entityManager;
    private final Clock clock;

    public VoucherService(VoucherRepository vouchers, VoucherUsageRepository usages,
            CurrentAccountIdProvider accounts, EntityManager entityManager, Clock clock) {
        this.vouchers = vouchers;
        this.usages = usages;
        this.accounts = accounts;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Advisory eligibility only. No row is reserved and no redemption is written. */
    @Transactional(readOnly = true)
    public VoucherApplication preview(String input, Long shop, BigDecimal subtotal) {
        String code = VoucherCode.normalize(input);
        if (code.isEmpty()) return null;
        var voucher = vouchers.findByCode(code).orElseThrow(() -> new ApplicationException(ErrorCode.Detail.VOUCHER_UNKNOWN));
        return validate(voucher, buyer(), shop, subtotal);
    }

    /**
     * Placement order: buyer -> cart -> ascending products -> shops -> categories -> voucher -> new order/payment.
     * Lock before counting both limits; every redemption writer uses this same row lock. READ COMMITTED
     * counts after a lock wait see the preceding checkout's commit. Refresh excludes stale preview state.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public VoucherApplication lockForCheckout(String input, Long shop, BigDecimal subtotal) {
        String code = VoucherCode.normalize(input);
        if (code.isEmpty()) return null;
        if (TransactionSynchronizationManager.hasResource(LOCKED_RESOURCE)) throw new ApplicationException(ErrorCode.CONFLICT);
        var voucher = vouchers.findByCodeForUpdate(code).orElseThrow(() -> new ApplicationException(ErrorCode.Detail.VOUCHER_UNKNOWN));
        entityManager.refresh(voucher, LockModeType.PESSIMISTIC_WRITE);
        Long buyer = buyer();
        var application = validate(voucher, buyer, shop, subtotal);
        TransactionSynchronizationManager.bindResource(LOCKED_RESOURCE, new LockedVoucher(application, buyer));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(LOCKED_RESOURCE);
            }
        });
        return application;
    }

    /** Requires the exact checked application and authenticated buyer in the same outer transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUsage(VoucherApplication application, Long orderId) {
        Object resource = TransactionSynchronizationManager.getResource(LOCKED_RESOURCE);
        if (!(resource instanceof LockedVoucher locked) || locked.redeemed || orderId == null || orderId <= 0
                || !locked.application.equals(application) || !locked.buyer.equals(buyer())) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        usages.saveAndFlush(new VoucherUsage(application, locked.buyer, orderId,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        locked.redeemed = true;
    }

    private VoucherApplication validate(Voucher voucher, Long buyer, Long shop, BigDecimal subtotal) {
        var application = voucher.apply(shop, subtotal, clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (usages.countByVoucherIdAndStatus(voucher.getId(), VoucherUsage.Status.REDEEMED) >= voucher.getTotalLimit()) throw new ApplicationException(ErrorCode.Detail.VOUCHER_QUOTA);
        if (usages.countByVoucherIdAndUserIdAndStatus(voucher.getId(), buyer, VoucherUsage.Status.REDEEMED) >= voucher.getPerUserLimit()) throw new ApplicationException(ErrorCode.Detail.VOUCHER_USER_LIMIT);
        return application;
    }

    /**
     * Internal lifecycle effect, not an HTTP command. Caller has authorized cancellation and locked
     * vendor account -> existing Order -> ascending products -> Shop. Reacquire that same Order,
     * then lock Voucher -> VoucherUsage. Never acquire products/Shop or the buyer account here.
     * No current eligibility check: edited/inactive/expired vouchers still release their usage.
     * Returns/refunds after delivery never call this boundary and are also rejected by its guard.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForCancellation(Long orderId) {
        if (orderId == null || orderId <= 0) throw new ApplicationException(ErrorCode.CONFLICT);
        var order = usages.lockCancellationOrderFacts(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONFLICT));
        if (!("NEW".equals(order.getStatus()) || "CONFIRMED".equals(order.getStatus()))
                || order.getDeliveredAt() != null) throw new ApplicationException(ErrorCode.CONFLICT);
        var recorded = usages.findByOrderId(orderId);
        if (order.getVoucherId() == null && recorded.isEmpty()) return; // Legacy/no-voucher: no invented history.
        if (order.getVoucherId() == null || recorded.isEmpty()) throw new ApplicationException(ErrorCode.CONFLICT);
        vouchers.findByIdForUpdate(order.getVoucherId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONFLICT));
        var usage = usages.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONFLICT));
        entityManager.refresh(usage, LockModeType.PESSIMISTIC_WRITE);
        if (!order.getVoucherId().equals(usage.getVoucherId()) || !order.getBuyerId().equals(usage.getUserId())
                || order.getDiscountTotal().compareTo(usage.getDiscountAmount()) != 0) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        usage.release(clock.instant().truncatedTo(ChronoUnit.MICROS));
        // Managed update flushes with the lifecycle Order/status; never commit independently.
    }

    private Long buyer() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static final class LockedVoucher {
        final VoucherApplication application;
        final Long buyer;
        boolean redeemed;
        LockedVoucher(VoucherApplication application, Long buyer) { this.application = application; this.buyer = buyer; }
    }
}
