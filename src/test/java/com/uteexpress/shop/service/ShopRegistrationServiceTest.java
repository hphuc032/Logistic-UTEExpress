package com.uteexpress.shop.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.dto.ShopRegistrationRequest;
import com.uteexpress.shop.entity.Shop;
import com.uteexpress.shop.entity.ShopStatus;
import com.uteexpress.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopRegistrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-23T09:02:00Z");

    @Mock ShopRepository shops;
    @Mock CurrentAccountIdProvider accountIds;
    ShopRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new ShopRegistrationService(shops, accountIds, Clock.fixed(NOW, ZoneOffset.UTC));
        when(accountIds.currentAccountId()).thenReturn(Optional.of(42L));
    }

    @Test
    void normalizesInputAndCreatesPendingApplicationForAuthenticatedOwner() {
        when(shops.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new ShopRegistrationRequest(
                "  UTE Tech  ", "  My-Shop  ", "  Thiết bị sinh viên  ", "  01 Võ Văn Ngân  "));

        ArgumentCaptor<Shop> saved = ArgumentCaptor.forClass(Shop.class);
        verify(shops).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(42L);
        assertThat(saved.getValue().getName()).isEqualTo("UTE Tech");
        assertThat(saved.getValue().getSlug()).isEqualTo("my-shop");
        assertThat(saved.getValue().getDescription()).isEqualTo("Thiết bị sinh viên");
        assertThat(saved.getValue().getPickupAddress()).isEqualTo("01 Võ Văn Ngân");
        assertThat(saved.getValue().getStatus()).isEqualTo(ShopStatus.PENDING);
        assertThat(saved.getValue().getRejectionReason()).isNull();
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void convertsBlankOptionalDescriptionToNull() {
        when(shops.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new ShopRegistrationRequest("Shop", "shop", "   ", "Pickup"));

        ArgumentCaptor<Shop> saved = ArgumentCaptor.forClass(Shop.class);
        verify(shops).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDescription()).isNull();
    }

    @Test
    void rejectsExistingOwnerBeforePersistence() {
        when(shops.existsByOwnerId(42L)).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(shops, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCanonicalDuplicateSlug() {
        when(shops.existsBySlug("my-shop")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new ShopRegistrationRequest("Shop", "My-Shop", null, "Pickup")))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(shops, never()).saveAndFlush(any());
    }

    @Test
    void convertsDatabaseUniqueRaceToSafeConflict() {
        when(shops.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_shops_owner_id"));

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOfSatisfying(ApplicationException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(error.getMessage()).doesNotContain("uq_shops_owner_id");
                });
    }

    @Test
    void missingAuthenticatedAccountFailsWithPublicUnauthenticatedCode() {
        when(accountIds.currentAccountId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private static ShopRegistrationRequest request() {
        return new ShopRegistrationRequest("Shop", "shop", null, "Pickup");
    }
}
