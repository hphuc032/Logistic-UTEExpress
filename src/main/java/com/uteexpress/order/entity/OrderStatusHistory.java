package com.uteexpress.order.entity;

import com.uteexpress.order.dto.OrderStatus;
import java.util.Objects;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status")
    private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;
    @Column(name = "reason", columnDefinition = "text")
    private String reason;
    @Column(name = "actor_id")
    private Long actorId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected OrderStatusHistory() { }
    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public Long getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }

    /** Trusted lifecycle history; null actor is reserved for approved system actions. */
    public OrderStatusHistory(Long orderId, OrderStatus fromStatus,
            OrderStatus toStatus, Long actorId, Instant at, String reason) {
        this.orderId = Objects.requireNonNull(orderId);
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus);
        this.actorId = actorId;
        this.createdAt = Objects.requireNonNull(at);
        this.reason = reason;
    }

}
