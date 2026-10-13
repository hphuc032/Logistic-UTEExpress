package com.uteexpress.order.repository;

import com.uteexpress.order.entity.ReturnRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Optional<ReturnRequest> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.orderId = :orderId")
    Optional<ReturnRequest> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
