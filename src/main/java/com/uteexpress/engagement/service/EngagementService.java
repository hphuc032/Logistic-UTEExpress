package com.uteexpress.engagement.service;

import com.uteexpress.catalog.dto.CartProductSnapshot;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.engagement.dto.SavedProductView;
import com.uteexpress.engagement.repository.EngagementRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class EngagementService {
    private final EngagementRepository engagement;
    private final CatalogQueryService catalog;
    private final CurrentAccountIdProvider accountIds;
    private final Clock clock;

    public EngagementService(EngagementRepository engagement, CatalogQueryService catalog,
            CurrentAccountIdProvider accountIds, Clock clock) {
        this.engagement = engagement;
        this.catalog = catalog;
        this.accountIds = accountIds;
        this.clock = clock;
    }

    @Transactional
    public void addFavorite(Long productId) {
        requireId(productId);
        Long userId = accountId();
        catalog.requirePurchasableProducts(Set.of(productId));
        engagement.addFavorite(userId, productId, Instant.now(clock));
    }

    @Transactional
    public void removeFavorite(Long productId) {
        requireId(productId);
        engagement.removeFavorite(accountId(), productId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long productId) {
        requireId(productId);
        return engagement.isFavorite(accountId(), productId);
    }

    @Transactional(readOnly = true)
    public List<SavedProductView> favorites() { return display(engagement.favorites(accountId())); }

    @Transactional
    public void recordView(Long productId) {
        requireId(productId);
        Long userId = accountId();
        catalog.requirePurchasableProducts(Set.of(productId));
        engagement.recordView(userId, productId, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<SavedProductView> recent() { return display(engagement.recent(accountId())); }

    private List<SavedProductView> display(List<EngagementRepository.SavedRow> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, CartProductSnapshot> products = catalog.findCartProducts(rows.stream()
                .map(EngagementRepository.SavedRow::productId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CartProductSnapshot::productId, Function.identity()));
        return rows.stream().map(row -> {
            CartProductSnapshot product = products.get(row.productId());
            boolean available = product != null && product.purchasable();
            return new SavedProductView(row.productId(), available ? product.productName() : "Sản phẩm không còn hiển thị",
                    available ? product.finalUnitPrice() : null, available, row.savedAt());
        }).toList();
    }

    private Long accountId() {
        return accountIds.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static void requireId(Long productId) {
        if (productId == null || productId <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
    }
}
