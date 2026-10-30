package com.uteexpress.cart.service;

import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.entity.Cart;
import com.uteexpress.cart.entity.CartItem;
import com.uteexpress.cart.repository.CartRepository;
import com.uteexpress.cart.repository.CartItemRepository;
import com.uteexpress.catalog.dto.CartProductSnapshot;
import com.uteexpress.catalog.dto.StockQuantity;
import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

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
    private final InventoryService inventory;

    public CartService(CartRepository carts, CartItemRepository items, CurrentAccountIdProvider accountIds,
            CatalogQueryService catalog, InventoryService inventory, Clock clock) {
        this.carts = carts;
        this.items = items;
        this.accountIds = accountIds;
        this.catalog = catalog;
        this.clock = clock;
        this.inventory = inventory;
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

    /** CHK-02: retain the same cart lock used by every cart mutation until checkout commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CartItemView> lockSelectedItemsForCheckout() {
        Long owner = ownerId();
        Cart cart = carts.lockByUserId(owner)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_REQUEST));
        return view(cart, owner).items().stream().filter(CartItemView::selected).toList();
    }

    /** Internal checkout boundary: remove only the exact locked lines represented by the order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeCheckedOutItems(List<CartItemView> checkedOut) {
        if (checkedOut == null || checkedOut.isEmpty()) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        Long owner = ownerId();
        Cart cart = lockExisting(owner);
        List<CartItem> removed = checkedOut.stream().map(line -> {
            CartItem item = ownedItem(line.id(), owner);
            if (!item.isSelected() || !item.getProductId().equals(line.productId())
                    || item.getQuantity() != line.quantity()) throw new ApplicationException(ErrorCode.CONFLICT);
            return item;
        }).toList();
        items.deleteAll(removed);
        cart.touch(clock.instant());
        items.flush();
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
        int quantity;
        try {
            quantity = Math.addExact(item == null ? 0 : item.getQuantity(), request.quantity());
        } catch (ArithmeticException overflow) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        inventory.lockAndCheck(List.of(new StockQuantity(request.productId(), quantity)));
        if (item == null) {
            items.save(CartItem.create(cart, request.productId(), request.quantity(), now));
        } else {
            item.updateQuantity(quantity, now);
        }
        cart.touch(now);
        return view(cart, owner);
    }

    private Cart lockOrCreate(Long owner) {
        carts.createIfAbsent(owner);
        return carts.lockByUserId(owner).orElseThrow(() -> new ApplicationException(ErrorCode.CONFLICT));
    }

    @Transactional
    public CartView updateQuantity(Long itemId, UpdateCartQuantityRequest request) {
        Long owner = ownerId();
        if (request == null || request.quantity() == null || request.quantity() <= 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        Cart cart = lockExisting(owner);
        CartItem item = ownedItem(itemId, owner);
        inventory.lockAndCheck(List.of(new StockQuantity(item.getProductId(), request.quantity())));
        Instant now = Instant.now(clock);
        item.updateQuantity(request.quantity(), now);
        cart.touch(now);
        return view(cart, owner);
    }

    @Transactional
    public CartView removeItem(Long itemId) {
        Long owner = ownerId();
        Cart cart = lockExisting(owner);
        items.delete(ownedItem(itemId, owner));
        cart.touch(Instant.now(clock));
        return view(cart, owner);
    }

    @Transactional
    public CartView selectItem(Long itemId, SelectCartItemRequest request) {
        Long owner = ownerId();
        if (request == null || request.selected() == null) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        Cart cart = lockExisting(owner);
        CartItem item = ownedItem(itemId, owner);
        Instant now = Instant.now(clock);
        // Selection is a durable preference, never proof of current availability.
        item.select(request.selected(), now);
        cart.touch(now);
        return view(cart, owner);
    }

    private Cart lockExisting(Long owner) {
        return carts.lockByUserId(owner)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private CartItem ownedItem(Long itemId, Long owner) {
        if (itemId == null || itemId <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return items.findByIdAndCartUserId(itemId, owner)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Long ownerId() {
        return accountIds.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private CartView view(Cart cart, Long owner) {
        List<CartItem> lines = items.findAllByCartIdAndCartUserIdOrderById(cart.getId(), owner);
        if (lines.isEmpty()) return new CartView(cart.getId(), List.of(), BigDecimal.ZERO);
        Set<Long> ids = lines.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<Long, CartProductSnapshot> prices = catalog.findCartProducts(ids).stream()
                .collect(Collectors.toMap(CartProductSnapshot::productId, Function.identity()));
        List<CartItemView> views = lines.stream().map(line -> {
            CartProductSnapshot product = prices.get(line.getProductId());
            CartItemStatus status = product == null || !product.purchasable() ? CartItemStatus.UNAVAILABLE
                    : product.stock() == 0 ? CartItemStatus.OUT_OF_STOCK
                    : product.stock() < line.getQuantity() ? CartItemStatus.INSUFFICIENT_STOCK
                    : CartItemStatus.AVAILABLE;
            return new CartItemView(line.getId(), line.getProductId(),
                    product == null ? "Sản phẩm #" + line.getProductId() : product.productName(),
                    line.getQuantity(), line.isSelected(), product == null ? null : product.finalUnitPrice(),
                    status == CartItemStatus.AVAILABLE
                            ? product.finalUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())) : BigDecimal.ZERO,
                    product == null ? 0 : product.stock(), status,
                    product == null ? null : product.unitPrice(), product == null ? BigDecimal.ZERO : product.discountSnapshot());
        }).toList();
        return new CartView(cart.getId(), views,
                views.stream().map(CartItemView::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
