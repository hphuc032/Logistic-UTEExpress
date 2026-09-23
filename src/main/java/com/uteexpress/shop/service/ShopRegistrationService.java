package com.uteexpress.shop.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.dto.ShopRegistrationRequest;
import com.uteexpress.shop.dto.ShopView;
import com.uteexpress.shop.entity.Shop;
import com.uteexpress.shop.repository.ShopRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@Validated
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class ShopRegistrationService {
    private final ShopRepository shops;
    private final CurrentAccountIdProvider accountIds;
    private final Clock clock;

    public ShopRegistrationService(ShopRepository shops, CurrentAccountIdProvider accountIds, Clock clock) {
        this.shops = shops;
        this.accountIds = accountIds;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ShopView> currentShop() {
        return shops.findByOwnerId(ownerId()).map(ShopRegistrationService::view);
    }

    @Transactional
    public ShopView register(@NotNull @Valid ShopRegistrationRequest request) {
        Long ownerId = ownerId();
        if (shops.existsByOwnerId(ownerId)) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }

        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        String description = blankToNull(request.description());
        String pickupAddress = request.pickupAddress().trim();
        if (shops.existsBySlug(slug)) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }

        Shop application = Shop.pendingApplication(
                ownerId, name, slug, description, pickupAddress, Instant.now(clock));
        try {
            return view(shops.saveAndFlush(application));
        } catch (DataIntegrityViolationException conflict) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    private Long ownerId() {
        return accountIds.currentAccountId()
                .filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static ShopView view(Shop shop) {
        return new ShopView(shop.getId(), shop.getName(), shop.getSlug(), shop.getDescription(),
                shop.getPickupAddress(), shop.getStatus().name(), shop.getRejectionReason(), shop.getVersion());
    }
}
