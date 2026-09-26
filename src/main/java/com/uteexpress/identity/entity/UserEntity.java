package com.uteexpress.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "users", schema = "uteexpress")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, unique = true, length = 254)
    private String normalizedEmail;

    @Column(name = "username", nullable = false, length = 30)
    private String username;

    @Column(name = "normalized_username", nullable = false, unique = true, length = 30)
    private String normalizedUsername;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "avatar_key", length = 512)
    private String avatarKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected UserEntity() {
    }

    public static UserEntity pendingRegistration(String email, String normalizedEmail,
            String username, String normalizedUsername, String passwordHash, Instant now) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.normalizedEmail = normalizedEmail;
        user.username = username;
        user.normalizedUsername = normalizedUsername;
        user.passwordHash = passwordHash;
        user.status = UserStatus.PENDING_VERIFICATION;
        user.emailVerifiedAt = null;
        user.tokenVersion = 0;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public void activateEmail(Instant now) {
        if (status != UserStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only a pending account can be activated");
        }
        status = UserStatus.ACTIVE;
        emailVerifiedAt = now;
        updatedAt = now;
    }

    public void resetPassword(String newPasswordHash, Instant now) {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("Only an active account can reset its password");
        }
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        passwordHash = newPasswordHash;
        tokenVersion++;
        updatedAt = now;
    }

    public void updateProfile(String fullName, String phone, Instant now) {
        this.fullName = fullName;
        this.phone = phone;
        this.updatedAt = now;
    }

    public String replaceAvatarKey(String newAvatarKey, Instant now) {
        String previous = avatarKey;
        avatarKey = newAvatarKey;
        updatedAt = now;
        return previous;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        resetPassword(newPasswordHash, now);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getUsername() { return username; }
    public String getNormalizedUsername() { return normalizedUsername; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getAvatarKey() { return avatarKey; }
    public UserStatus getStatus() { return status; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public long getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
