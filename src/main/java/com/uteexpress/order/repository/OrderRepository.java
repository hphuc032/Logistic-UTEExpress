package com.uteexpress.order.repository;

import com.uteexpress.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select o.id from Order o where o.buyerId = :buyerId
            and o.id = (select i.orderId from OrderItem i where i.id = :itemId)
            """)
    java.util.Optional<Long> findOwnedOrderIdForItem(
            @org.springframework.data.repository.query.Param("itemId") Long itemId,
            @org.springframework.data.repository.query.Param("buyerId") Long buyerId);

    // Scalar query: read current persisted status rather than a cached Order entity.
    @org.springframework.data.jpa.repository.Query("""
            select count(o) > 0 from Order o where o.buyerId = :buyerId
            and o.status = com.uteexpress.order.dto.OrderStatus.DELIVERED
            and o.id = (select i.orderId from OrderItem i where i.id = :itemId)
            """)
    boolean existsDeliveredItemForBuyer(
            @org.springframework.data.repository.query.Param("itemId") Long itemId,
            @org.springframework.data.repository.query.Param("buyerId") Long buyerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id and o.buyerId = :buyerId")
    java.util.Optional<Order> findByIdAndBuyerIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("buyerId") Long buyerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Page<Order> findByShopId(Long shopId, Pageable pageable);
    java.util.Optional<Order> findByIdAndShopId(Long id, Long shopId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id and o.shopId = :shopId")
    java.util.Optional<Order> findByIdAndShopIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("shopId") Long shopId);
    java.util.Optional<Order> findByBuyerIdAndCheckoutKey(Long buyerId, String checkoutKey);
    Page<Order> findByBuyerId(Long buyerId, Pageable pageable);
    java.util.Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);
}
