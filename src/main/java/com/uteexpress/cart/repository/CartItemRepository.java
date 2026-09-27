package com.uteexpress.cart.repository;

import com.uteexpress.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findAllByCartIdAndCartUserIdOrderById(Long cartId, Long userId);
    Optional<CartItem> findByCartIdAndProductIdAndCartUserId(Long cartId, Long productId, Long userId);
    Optional<CartItem> findByIdAndCartUserId(Long id, Long userId);
}
