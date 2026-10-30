package com.uteexpress.shop.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShopTest {
    @Test
    void pendingApplicationFixesSecuritySensitiveInitialState() {
        Instant now = Instant.parse("2026-09-23T09:02:00Z");

        Shop shop = Shop.pendingApplication(42L, "UTE Tech", "ute-tech", "Thiết bị", "01 Võ Văn Ngân", now);

        assertThat(shop.getOwnerId()).isEqualTo(42L);
        assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING);
        assertThat(shop.getRejectionReason()).isNull();
        assertThat(shop.getLogoKey()).isNull();
        assertThat(shop.getBannerKey()).isNull();
        assertThat(shop.getCreatedAt()).isEqualTo(now);
        assertThat(shop.getUpdatedAt()).isEqualTo(now);
    }
}
