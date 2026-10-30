package com.uteexpress.order.entity;

import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.checkout.dto.Money;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.util.Objects;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "discount_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountSnapshot;
    @Column(name = "final_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalUnitPrice;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;
    @Column(name = "quantity", nullable = false)
    private int quantity;
    protected OrderItem() { }
    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountSnapshot() { return discountSnapshot; }
    public BigDecimal getFinalUnitPrice() { return finalUnitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public int getQuantity() { return quantity; }

    /** Trusted checkout snapshot, never a browser write model. */
    public OrderItem(Long orderId, CheckoutQuote.ItemSnapshot snapshot) {
        this(snapshot);
        this.orderId = Objects.requireNonNull(orderId);
        if (orderId <= 0) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED);
        }
    }

    static BigDecimal validatedLineTotal(CheckoutQuote.ItemSnapshot snapshot) {
        return new OrderItem(snapshot).lineTotal;
    }

    private OrderItem(CheckoutQuote.ItemSnapshot snapshot) {
        productId = Objects.requireNonNull(snapshot.productId());
        productNameSnapshot = Objects.requireNonNull(snapshot.productNameSnapshot());
        unitPrice = Money.requireAmount(snapshot.unitPrice());
        discountSnapshot = Money.requireAmount(snapshot.discountSnapshot());
        finalUnitPrice = Money.requireAmount(snapshot.finalUnitPrice());
        lineTotal = Money.requireAmount(snapshot.lineTotal());
        quantity = snapshot.quantity();
        if (productId <= 0 || productNameSnapshot.isBlank() || quantity < 1
                || unitPrice.signum() <= 0 || unitPrice.subtract(discountSnapshot).compareTo(finalUnitPrice) != 0
                || finalUnitPrice.multiply(BigDecimal.valueOf(quantity)).compareTo(lineTotal) != 0) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED);
        }
    }

}
