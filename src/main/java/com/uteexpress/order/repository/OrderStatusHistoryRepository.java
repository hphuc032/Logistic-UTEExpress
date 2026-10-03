package com.uteexpress.order.repository;

import com.uteexpress.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    java.util.List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
