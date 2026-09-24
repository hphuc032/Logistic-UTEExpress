package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.repository.CatalogReadRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class DatabaseCatalogQueryService implements CatalogQueryService {
    private final CatalogReadRepository catalog;

    public DatabaseCatalogQueryService(CatalogReadRepository catalog) {
        this.catalog = catalog;
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
}
