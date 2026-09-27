package com.uteexpress.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "addresses", schema = "uteexpress")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "receiver_name", nullable = false, length = 120)
    private String receiverName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(name = "province_code", nullable = false, length = 64)
    private String provinceCode;

    @Column(nullable = false, length = 120)
    private String district;

    @Column(nullable = false, length = 255)
    private String detail;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Address() {
    }

    public static Address create(Long userId, String receiverName, String phone,
            String provinceCode, String district, String detail, boolean defaultAddress, Instant now) {
        Address address = new Address();
        address.userId = Objects.requireNonNull(userId, "userId");
        address.updateDetails(receiverName, phone, provinceCode, district, detail, now);
        address.defaultAddress = defaultAddress;
        address.createdAt = Objects.requireNonNull(now, "now");
        return address;
    }

    public void updateDetails(String receiverName, String phone, String provinceCode,
            String district, String detail, Instant now) {
        this.receiverName = requireText(receiverName, "receiverName");
        this.phone = requireText(phone, "phone");
        this.provinceCode = requireText(provinceCode, "provinceCode");
        this.district = requireText(district, "district");
        this.detail = requireText(detail, "detail");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void makeDefault(Instant now) {
        defaultAddress = true;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getReceiverName() { return receiverName; }
    public String getPhone() { return phone; }
    public String getProvinceCode() { return provinceCode; }
    public String getDistrict() { return district; }
    public String getDetail() { return detail; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
