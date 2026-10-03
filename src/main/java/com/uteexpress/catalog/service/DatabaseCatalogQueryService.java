package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.dto.CartProductSnapshot;
import com.uteexpress.catalog.repository.CatalogReadRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.service.CategoryQueryService;
import com.uteexpress.shop.service.ShopAvailabilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class DatabaseCatalogQueryService implements CatalogQueryService {
    private final CatalogReadRepository catalog;
    private final ShopAvailabilityService shops;
    private final CategoryQueryService categories;

    public DatabaseCatalogQueryService(CatalogReadRepository catalog, ShopAvailabilityService shops,
            CategoryQueryService categories) {
        this.catalog = catalog;
        this.shops = shops;
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartProductSnapshot> findCartProducts(Set<Long> productIds) {
        if (productIds == null || productIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        return productIds.isEmpty() ? List.of() : List.copyOf(catalog.findCartProducts(new TreeSet<>(productIds)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSnapshot> requirePurchasableProducts(Set<Long> productIds) {
        if (productIds == null || productIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        if (productIds.isEmpty()) {
            return List.of();
        }

        Set<Long> orderedIds = new TreeSet<>(productIds);
        List<ProductSnapshot> snapshots = catalog.findPurchasableByIds(orderedIds);
        if (snapshots.size() != orderedIds.size()) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return List.copyOf(snapshots);
    }

    @Override
    @Transactional
    public List<ProductSnapshot> requirePurchasableProductsForCheckout(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()
                || productIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        Set<Long> orderedIds = new TreeSet<>(productIds);
        List<CatalogReadRepository.CheckoutAvailabilityKey> keys = catalog.findCheckoutAvailabilityKeys(orderedIds);
        if (keys.size() != orderedIds.size()) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);

        // Global checkout lock order after product locks: shop IDs ascending, then category IDs ascending.
        keys.stream().map(CatalogReadRepository.CheckoutAvailabilityKey::shopId).distinct().sorted()
                .forEach(shops::requireApprovedForCheckout);
        keys.stream().map(CatalogReadRepository.CheckoutAvailabilityKey::categoryId).distinct().sorted()
                .forEach(categories::requireActiveForCheckout);

        // The locks above exclude concurrent availability mutations; this query sees their committed state.
        return requirePurchasableProducts(orderedIds);
    }
}
