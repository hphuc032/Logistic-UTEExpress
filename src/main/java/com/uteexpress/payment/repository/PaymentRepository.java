package com.uteexpress.payment.repository;

import com.uteexpress.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    java.util.List<Payment> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId order by p.id")
    List<Payment> lockRecordsForOrder(@Param("orderId") Long orderId);

    // Scalar TD-owned order facts keep Order -> Payment acyclic; no foreign JPA entity is exposed.
    // This stable parent lock also serializes creation when no payment row exists yet.
    @Query(value = """
            SELECT status, subtotal, discount_total AS "discountTotal",
                   shipping_fee AS "shippingFee", grand_total AS "grandTotal"
            FROM uteexpress.orders WHERE id = :orderId FOR UPDATE
            """, nativeQuery = true)
    Optional<CodOrderFacts> lockOrderFacts(@Param("orderId") Long orderId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM uteexpress.orders WHERE id = :orderId AND buyer_id = :buyerId)",
            nativeQuery = true)
    boolean buyerOwnsOrder(@Param("orderId") Long orderId, @Param("buyerId") Long buyerId);

    interface CodOrderFacts {
        String getStatus();
        BigDecimal getSubtotal();
        BigDecimal getDiscountTotal();
        BigDecimal getShippingFee();
        BigDecimal getGrandTotal();
    }
}
