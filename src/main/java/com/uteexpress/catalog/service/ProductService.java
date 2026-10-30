package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductCreateRequest;
import com.uteexpress.catalog.dto.ProductImageView;
import com.uteexpress.catalog.dto.ProductUpdateRequest;
import com.uteexpress.catalog.dto.ProductView;
import com.uteexpress.catalog.entity.Product;
import com.uteexpress.catalog.entity.ProductImage;
import com.uteexpress.catalog.repository.ProductImageRepository;
import com.uteexpress.catalog.repository.ProductRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.FileStorageService;
import com.uteexpress.common.storage.ImageStoragePolicy;
import com.uteexpress.common.storage.StorageException;
import com.uteexpress.common.storage.StoredContent;
import com.uteexpress.common.storage.StoredFile;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.governance.dto.CategoryOption;
import com.uteexpress.governance.service.CategoryQueryService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.dto.VendorShopData;
import com.uteexpress.shop.service.VendorShopQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class ProductService {
    public static final int MAX_IMAGES = 5;
    public static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    public static final int IMAGE_MAX_DIMENSION = 4096;
    private static final ImageStoragePolicy IMAGE_POLICY =
            new ImageStoragePolicy(IMAGE_MAX_BYTES, IMAGE_MAX_DIMENSION, IMAGE_MAX_DIMENSION);

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CurrentAccountIdProvider accountIds;
    private final VendorShopQueryService shops;
    private final CategoryQueryService categories;
    private final FileStorageService storage;
    private final Clock clock;

    public ProductService(ProductRepository products, ProductImageRepository images,
            CurrentAccountIdProvider accountIds, VendorShopQueryService shops,
            CategoryQueryService categories, FileStorageService storage, Clock clock) {
        this.products = products;
        this.images = images;
        this.accountIds = accountIds;
        this.shops = shops;
        this.categories = categories;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProductView> listCurrentShopProducts() {
        VendorShopData shop = approvedShop();
        List<Product> owned = products.findAllByShopIdOrderByIdDesc(shop.shopId());
        Map<Long, CategoryOption> categoryMap = categories.findCategories(
                owned.stream().map(Product::getCategoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CategoryOption::id, Function.identity()));
        return owned.stream().map(product -> view(product, categoryMap.get(product.getCategoryId()))).toList();
    }

    @Transactional(readOnly = true)
    public ProductView getCurrentShopProduct(Long productId) {
        Product product = owned(productId, approvedShop().shopId());
        CategoryOption category = categories.findCategories(List.of(product.getCategoryId())).stream()
                .findFirst().orElse(null);
        return view(product, category);
    }

    @Transactional
    public ProductView create(@NotNull @Valid ProductCreateRequest request) {
        VendorShopData shop = approvedShop();
        CategoryOption category = categories.requireActiveCategory(request.getCategoryId());
        Product product = Product.create(shop.shopId(), category.id(), request.getName(), request.getDescription(),
                request.getPrice(), request.getStock(), now());
        return view(persist(product), category);
    }

    @Transactional
    public ProductView update(Long productId, @NotNull @Valid ProductUpdateRequest request) {
        VendorShopData shop = approvedShop();
        CategoryOption category = categories.requireActiveCategory(request.getCategoryId());
        Product product = owned(productId, shop.shopId());
        requireVersion(product, request.getVersion());
        product.updateDetails(category.id(), request.getName(), request.getDescription(), request.getPrice(),
                request.getStock(), now());
        return view(persist(product), category);
    }

    @Transactional
    public void hide(Long productId, Long expectedVersion) {
        VendorShopData shop = approvedShop();
        Product product = owned(productId, shop.shopId());
        requireVersion(product, expectedVersion);
        product.hide(now());
        persist(product);
    }

    @Transactional
    public ProductImageView addImage(Long productId, @NotNull UploadContent upload, String altText) {
        VendorShopData shop = approvedShop();
        Product product = ownedForUpdate(productId, shop.shopId());
        List<ProductImage> current = images.findByProductIdOrderByPositionAsc(product.getId());
        if (current.size() >= MAX_IMAGES) throw new ApplicationException(ErrorCode.CONFLICT);
        String normalizedAlt = normalizeAlt(altText);
        final StoredFile stored;
        try {
            stored = storage.storeImage("products", upload, IMAGE_POLICY);
        } catch (StorageException invalidImage) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        registerRollbackCleanup(stored.key());
        int position = current.stream().mapToInt(ProductImage::getPosition).max().orElse(-1) + 1;
        try {
            return imageView(images.saveAndFlush(
                    ProductImage.create(product.getId(), stored.key(), position, normalizedAlt)));
        } catch (DataIntegrityViolationException conflict) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        VendorShopData shop = approvedShop();
        Product product = ownedForUpdate(productId, shop.shopId());
        ProductImage image = images.findByIdAndProductId(imageId, product.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        images.delete(image);
        images.flush();
        registerAfterCommitDelete(image.getStorageKey());
    }

    @Transactional(readOnly = true)
    public StoredContent readImage(Long productId, Long imageId) {
        VendorShopData shop = approvedShop();
        Product product = owned(productId, shop.shopId());
        ProductImage image = images.findByIdAndProductId(imageId, product.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            return storage.read(image.getStorageKey());
        } catch (StorageException missingFile) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private ProductView view(Product product, CategoryOption category) {
        String categoryName = category == null ? "Danh mục #" + product.getCategoryId() : category.name();
        boolean categoryActive = category != null && category.active();
        List<ProductImageView> productImages = images.findByProductIdOrderByPositionAsc(product.getId()).stream()
                .map(ProductService::imageView).toList();
        return new ProductView(product.getId(), product.getName(), product.getDescription(), product.getPrice(),
                product.getStock(), product.getStatus().name(), product.getCategoryId(), categoryName,
                categoryActive, product.getVersion(), productImages);
    }

    private Product owned(Long productId, Long shopId) {
        requireId(productId);
        return products.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Product ownedForUpdate(Long productId, Long shopId) {
        requireId(productId);
        return products.findByIdAndShopIdForUpdate(productId, shopId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Product persist(Product product) {
        try {
            return products.saveAndFlush(product);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException conflict) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    private VendorShopData approvedShop() {
        Long accountId = accountIds.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        return shops.requireApprovedOwnedShop(accountId);
    }

    private static void requireVersion(Product product, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        if (!Objects.equals(product.getVersion(), expectedVersion)) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
    }

    private static String normalizeAlt(String altText) {
        if (altText == null || altText.isBlank()) return null;
        String value = altText.trim();
        if (value.length() > 255 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        return value;
    }

    private void registerRollbackCleanup(String storageKey) {
        requireSynchronization();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(storageKey);
            }
        });
    }

    private void registerAfterCommitDelete(String storageKey) {
        requireSynchronization();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.delete(storageKey); }
        });
    }

    private static void requireSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Product image mutation requires transaction synchronization");
        }
    }

    private static ProductImageView imageView(ProductImage image) {
        return new ProductImageView(image.getId(), image.getPosition(), image.getAltText());
    }

    private Instant now() { return Instant.now(clock); }
}
