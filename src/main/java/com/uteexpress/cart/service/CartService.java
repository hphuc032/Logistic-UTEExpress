package com.uteexpress.cart.service;

import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.entity.Cart;
import com.uteexpress.cart.entity.CartItem;
import com.uteexpress.cart.repository.CartRepository;
import com.uteexpress.cart.repository.CartItemRepository;
import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class CartService {
    private final CartRepository carts;
    private final CartItemRepository items;
    private final CurrentAccountIdProvider accountIds;
    private final CatalogQueryService catalog;
    private final Clock clock;

    public CartService(CartRepository carts, CartItemRepository items, CurrentAccountIdProvider accountIds,
            CatalogQueryService catalog, Clock clock) {
        this.carts = carts;
        this.items = items;
        this.accountIds = accountIds;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public CartView getOrCreateCart() {
        Long owner = ownerId();
        return view(lockOrCreate(owner), owner);
    }

    @Transactional(readOnly = true)
    public Optional<CartView> getCurrentUserCart() {
        Long owner = ownerId();
        return carts.findByUserId(owner).map(cart -> view(cart, owner));
    }

    @Transactional
    public CartView addProduct(AddCartProductRequest request) {
        Long owner = ownerId();
        if (request == null || request.productId() == null || request.productId() <= 0
                || request.quantity() == null || request.quantity() <= 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        catalog.requirePurchasableProducts(Set.of(request.productId()));
        Cart cart = lockOrCreate(owner);
        Instant now = Instant.now(clock);
        CartItem item = items.findByCartIdAndProductIdAndCartUserId(cart.getId(), request.productId(), owner)
                .orElse(null);
        if (item == null) {
            items.save(CartItem.create(cart, request.productId(), request.quantity(), now));
        } else {
            try {
                item.addQuantity(request.quantity(), now);
            } catch (ArithmeticException overflow) {
                throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
            }
        }
        cart.touch(now);
        return view(cart, owner);
    }

    private Cart lockOrCreate(Long owner) {
        carts.createIfAbsent(owner);
        return carts.lockByUserId(owner).orElseThrow(() -> new ApplicationException(ErrorCode.CONFLICT));
    }

    private Long ownerId() {
        return accountIds.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private CartView view(Cart cart, Long owner) {
        List<CartItem> lines = items.findAllByCartIdAndCartUserIdOrderById(cart.getId(), owner);
        if (lines.isEmpty()) return new CartView(cart.getId(), List.of(), BigDecimal.ZERO);
        Set<Long> ids = lines.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<Long, ProductSnapshot> prices = catalog.requirePurchasableProducts(ids).stream()
                .collect(Collectors.toMap(ProductSnapshot::productId, Function.identity()));
        List<CartItemView> views = lines.stream().map(line -> {
            ProductSnapshot product = prices.get(line.getProductId());
            return new CartItemView(line.getId(), line.getProductId(), product.productName(),
                    line.getQuantity(), line.isSelected(), product.unitPrice(),
                    product.unitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }).toList();
        return new CartView(cart.getId(), views,
                views.stream().map(CartItemView::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
