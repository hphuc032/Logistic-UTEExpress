package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductCard;
import com.uteexpress.catalog.dto.ProductDetailView;
import com.uteexpress.catalog.dto.PublicCategorySummary;
import com.uteexpress.catalog.dto.PublicCategoryView;
import com.uteexpress.catalog.dto.PublicHomeView;
import com.uteexpress.catalog.dto.PublicShopSummary;
import com.uteexpress.catalog.dto.PublicShopView;
import com.uteexpress.catalog.repository.CatalogReadRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.FileStorageService;
import com.uteexpress.common.storage.StorageException;
import com.uteexpress.common.storage.StoredContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** Public read boundary. Visibility and safe not-found decisions are kept out of controllers. */
@Service
public class PublicCatalogService {
    public static final int HOME_PRODUCT_LIMIT = 12;
    private static final int MAX_SLUG_LENGTH = 120;
    private static final Pattern CANONICAL_SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final CatalogReadRepository catalog;
    private final FileStorageService storage;

    public PublicCatalogService(CatalogReadRepository catalog, FileStorageService storage) {
        this.catalog = catalog;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PublicHomeView home() {
        return new PublicHomeView(catalog.findFeaturedPublicProducts(HOME_PRODUCT_LIMIT),
                catalog.findPublicCategories(), catalog.findPublicShops());
    }

    @Transactional(readOnly = true)
    public List<ProductCard> products() {
        return List.copyOf(catalog.findAllPublicProducts());
    }

    @Transactional(readOnly = true)
    public ProductDetailView product(Long productId) {
        requirePositiveId(productId);
        ProductDetailView product = catalog.findPublicProduct(productId)
                .orElseThrow(PublicCatalogService::notFound);
        return product.withImages(catalog.findPublicProductImages(productId));
    }

    @Transactional(readOnly = true)
    public List<PublicShopSummary> shops() {
        return List.copyOf(catalog.findPublicShops());
    }

    @Transactional(readOnly = true)
    public PublicShopView shop(String slug) {
        String canonicalSlug = requireSlug(slug);
        PublicShopSummary shop = catalog.findPublicShop(canonicalSlug)
                .orElseThrow(PublicCatalogService::notFound);
        return new PublicShopView(shop.slug(), shop.name(), shop.description(),
                catalog.findPublicProductsByShopSlug(canonicalSlug));
    }

    @Transactional(readOnly = true)
    public PublicCategoryView category(String slug) {
        String canonicalSlug = requireSlug(slug);
        PublicCategorySummary category = catalog.findPublicCategory(canonicalSlug)
                .orElseThrow(PublicCatalogService::notFound);
        return new PublicCategoryView(category.slug(), category.name(),
                catalog.findPublicProductsByCategorySlug(canonicalSlug));
    }

    @Transactional(readOnly = true)
    public StoredContent image(Long productId, Long imageId) {
        requirePositiveId(productId);
        requirePositiveId(imageId);
        String storageKey = catalog.findPublicImageStorageKey(productId, imageId)
                .orElseThrow(PublicCatalogService::notFound);
        try {
            return storage.read(storageKey);
        } catch (StorageException unavailable) {
            throw notFound();
        }
    }

    private static void requirePositiveId(Long id) {
        if (id == null || id <= 0) throw notFound();
    }

    private static String requireSlug(String slug) {
        if (slug == null || slug.isBlank() || slug.length() > MAX_SLUG_LENGTH
                || !CANONICAL_SLUG.matcher(slug).matches()) {
            throw notFound();
        }
        return slug;
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
