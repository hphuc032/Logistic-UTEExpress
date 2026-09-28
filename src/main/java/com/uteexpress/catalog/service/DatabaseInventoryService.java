package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.StockQuantity;
import com.uteexpress.catalog.entity.Product;
import com.uteexpress.catalog.entity.ProductStatus;
import com.uteexpress.catalog.repository.ProductRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseInventoryService implements InventoryService {
    private static final Object LOCKED_BATCH_RESOURCE = DatabaseInventoryService.class.getName() + ".LOCKED_BATCH";
    private final ProductRepository products;
    private final CatalogQueryService catalog;
    private final Clock clock;

    public DatabaseInventoryService(ProductRepository products, CatalogQueryService catalog, Clock clock) {
        this.products = products;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndCheck(List<StockQuantity> quantities) {
        List<StockQuantity> requested = canonical(quantities);
        if (TransactionSynchronizationManager.hasResource(LOCKED_BATCH_RESOURCE)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        List<Long> ids = requested.stream().map(StockQuantity::productId).toList();
        List<Product> locked = products.findAllByIdForUpdate(ids);
        if (locked.size() != requested.size()) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        catalog.requirePurchasableProducts(new HashSet<>(ids));

        Map<Long, Product> byId = new LinkedHashMap<>();
        locked.forEach(product -> byId.put(product.getId(), product));
        for (StockQuantity item : requested) {
            Product product = byId.get(item.productId());
            if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
                throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (product.getStock() < item.quantity()) throw new ApplicationException(ErrorCode.CONFLICT);
        }

        LockedBatch batch = new LockedBatch(requested, Map.copyOf(byId));
        TransactionSynchronizationManager.bindResource(LOCKED_BATCH_RESOURCE, batch);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(LOCKED_BATCH_RESOURCE);
            }
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void decrease(List<StockQuantity> quantities) {
        List<StockQuantity> requested = canonical(quantities);
        Object resource = TransactionSynchronizationManager.getResource(LOCKED_BATCH_RESOURCE);
        if (!(resource instanceof LockedBatch batch) || batch.decreased || !batch.quantities.equals(requested)) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        Instant now = Instant.now(clock);
        for (StockQuantity item : requested) {
            try {
                batch.products.get(item.productId()).decreaseStock(item.quantity(), now);
            } catch (IllegalArgumentException invalidStock) {
                throw new ApplicationException(ErrorCode.CONFLICT);
            }
        }
        batch.decreased = true;
        products.flush();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(List<StockQuantity> quantities) {
        List<StockQuantity> requested = canonical(quantities);
        List<Long> ids = requested.stream().map(StockQuantity::productId).toList();
        List<Product> locked = products.findAllByIdForUpdate(ids);
        if (locked.size() != requested.size()) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        Map<Long, Product> byId = new HashMap<>();
        locked.forEach(product -> byId.put(product.getId(), product));
        Instant now = Instant.now(clock);
        for (StockQuantity item : requested) {
            try {
                byId.get(item.productId()).restoreStock(item.quantity(), now);
            } catch (ArithmeticException | IllegalArgumentException invalidStock) {
                throw new ApplicationException(ErrorCode.CONFLICT);
            }
        }
        products.flush();
    }

    private static List<StockQuantity> canonical(List<StockQuantity> quantities) {
        if (quantities == null || quantities.isEmpty() || quantities.stream().anyMatch(item -> item == null
                || item.productId() == null || item.productId() <= 0 || item.quantity() <= 0)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        List<StockQuantity> ordered = quantities.stream()
                .sorted(java.util.Comparator.comparing(StockQuantity::productId)).toList();
        if (ordered.stream().map(StockQuantity::productId).distinct().count() != ordered.size()) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        return List.copyOf(ordered);
    }

    private static final class LockedBatch {
        private final List<StockQuantity> quantities;
        private final Map<Long, Product> products;
        private boolean decreased;

        private LockedBatch(List<StockQuantity> quantities, Map<Long, Product> products) {
            this.quantities = quantities;
            this.products = products;
        }
    }
}
