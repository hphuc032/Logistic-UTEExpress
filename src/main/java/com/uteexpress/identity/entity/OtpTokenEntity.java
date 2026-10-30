package com.uteexpress.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "otp_tokens", schema = "uteexpress")
public class OtpTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected OtpTokenEntity() {
    }

    public static OtpTokenEntity issue(UserEntity user, OtpPurpose purpose, String codeHash,
            Instant sentAt, Instant expiresAt) {
        OtpTokenEntity token = new OtpTokenEntity();
        token.user = user;
        token.purpose = purpose;
        token.codeHash = codeHash;
        token.sentAt = sentAt;
        token.expiresAt = expiresAt;
        token.attempts = 0;
        return token;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean hasReachedAttemptLimit(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new IllegalStateException("OTP has already been consumed");
        }
        consumedAt = now;
    }

    public Long getId() { return id; }
    public UserEntity getUser() { return user; }
    public OtpPurpose getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public int getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
}
