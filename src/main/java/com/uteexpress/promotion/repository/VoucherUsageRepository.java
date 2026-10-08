package com.uteexpress.promotion.repository;

import com.uteexpress.promotion.entity.VoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    boolean existsByVoucherId(Long voucherId);
    long countByVoucherIdAndStatus(Long voucherId, VoucherUsage.Status status);
    long countByVoucherIdAndUserIdAndStatus(Long voucherId, Long userId, VoucherUsage.Status status);
    Optional<VoucherUsage> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from VoucherUsage u where u.orderId = :orderId")
    Optional<VoucherUsage> findByOrderIdForUpdate(Long orderId);

    // Scalar persisted facts preserve module boundaries. The lifecycle already holds this Order lock.
    // Do not flush the pending inventory-release marker separately: status/history share the final flush.
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query(value = """
            SELECT buyer_id AS "buyerId", voucher_id AS "voucherId", discount_total AS "discountTotal",
                   status, delivered_at AS "deliveredAt"
            FROM uteexpress.orders WHERE id = :orderId FOR UPDATE
            """, nativeQuery = true)
    Optional<CancellationOrderFacts> lockCancellationOrderFacts(Long orderId);

    interface CancellationOrderFacts {
        Long getBuyerId();
        Long getVoucherId();
        BigDecimal getDiscountTotal();
        String getStatus();
        Instant getDeliveredAt();
    }
}
