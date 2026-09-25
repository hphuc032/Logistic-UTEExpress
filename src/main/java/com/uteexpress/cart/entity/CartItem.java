package com.uteexpress.cart.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "cart_items", schema = "uteexpress",
        uniqueConstraints = @UniqueConstraint(name = "uq_cart_items_cart_id_product_id",
                columnNames = {"cart_id", "product_id"}))
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private Cart cart;
    // Catalog owns Product; the database FK preserves the relationship without a module dependency.
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private boolean selected;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() { }

    public static CartItem create(Cart cart, Long productId, int quantity, Instant now) {
        if (productId == null || productId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Product and quantity must be positive");
        }
        CartItem item = new CartItem();
        item.cart = Objects.requireNonNull(cart);
        item.productId = productId;
        item.quantity = quantity;
        item.selected = true;
        item.createdAt = Objects.requireNonNull(now);
        item.updatedAt = now;
        return item;
    }

    public void addQuantity(int added, Instant now) {
        if (added <= 0) throw new IllegalArgumentException("Quantity must be positive");
        quantity = Math.addExact(quantity, added);
        updatedAt = Objects.requireNonNull(now);
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public boolean isSelected() { return selected; }
}
