package com.uteexpress.order.repository;

import com.uteexpress.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    java.util.Optional<Order> findByBuyerIdAndCheckoutKey(Long buyerId, String checkoutKey);
    Page<Order> findByBuyerId(Long buyerId, Pageable pageable);
    java.util.Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);
}
