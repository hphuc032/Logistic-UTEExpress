package com.uteexpress.governance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Append-only historical record. Actor is a scalar FK, never a cross-module entity association. */
@Entity
@Immutable
@Table(name = "audit_logs", schema = "uteexpress")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_id", updatable = false)
    private Long actorId;
    @Column(nullable = false, length = 64, updatable = false)
    private String action;
    @Column(name = "target_type", nullable = false, length = 64, updatable = false)
    private String targetType;
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;
    @Column(name = "before_summary", nullable = false, length = 1000, updatable = false)
    private String beforeSummary;
    @Column(name = "after_summary", nullable = false, length = 1000, updatable = false)
    private String afterSummary;
    @Column(nullable = false, length = 64, updatable = false)
    private String reason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(Long actorId, String action, String targetType, Long targetId,
            String beforeSummary, String afterSummary, String reason, Instant createdAt) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeSummary = beforeSummary;
        this.afterSummary = afterSummary;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
}
