package com.uteexpress.cart.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** User is referenced by ID across the identity module boundary, enforced by a database FK. */
@Entity
@Table(name = "carts", schema = "uteexpress")
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version @Column(nullable = false)
    private Long version;

    protected Cart() { }

    public void touch(Instant now) { updatedAt = now; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
