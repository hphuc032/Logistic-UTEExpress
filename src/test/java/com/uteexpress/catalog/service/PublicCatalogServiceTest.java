package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductDetailView;
import com.uteexpress.catalog.dto.ProductImageView;
import com.uteexpress.catalog.dto.ProductSearchCriteria;
import com.uteexpress.catalog.dto.ProductSort;
import com.uteexpress.catalog.dto.PublicCategorySummary;
import com.uteexpress.catalog.dto.PublicShopSummary;
import com.uteexpress.catalog.repository.CatalogReadRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.FileStorageService;
import com.uteexpress.common.storage.StorageException;
import com.uteexpress.common.storage.StoredContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCatalogServiceTest {
    @Mock CatalogReadRepository repository;
    @Mock FileStorageService storage;
    PublicCatalogService service;

    @BeforeEach
    void setup() {
        service = new PublicCatalogService(repository, storage);
    }

    @Test
    void homeUsesBoundedReadModelsWithoutChangingCommerceContract() {
        when(repository.findFeaturedPublicProducts(PublicCatalogService.HOME_PRODUCT_LIMIT)).thenReturn(List.of());
        when(repository.findPublicCategories()).thenReturn(List.of(new PublicCategorySummary("dong-goi", "Đóng gói")));
        when(repository.findPublicShops()).thenReturn(List.of(new PublicShopSummary("shop-a", "Shop A", null)));

        var home = service.home();

        assertThat(home.products()).isEmpty();
        assertThat(home.categories()).extracting(PublicCategorySummary::slug).containsExactly("dong-goi");
        assertThat(home.shops()).extracting(PublicShopSummary::slug).containsExactly("shop-a");
    }

    @Test
    void productAddsOnlySafeOrderedImageMetadata() {
        ProductDetailView detail = new ProductDetailView(7L, "Product", "Description", new BigDecimal("10000"),
                2, "shop", "Shop", "category", "Category", List.of());
        when(repository.findPublicProduct(7L)).thenReturn(Optional.of(detail));
        when(repository.findPublicProductImages(7L)).thenReturn(List.of(new ProductImageView(9L, 0, "Alt")));

        assertThat(service.product(7L).images()).containsExactly(new ProductImageView(9L, 0, "Alt"));
    }

    @Test
    void invalidIdentifiersAndSlugsFailClosedAsNotFound() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertNotFound(() -> service.product(id));
            assertNotFound(() -> service.image(id, 1L));
        }
        for (String slug : new String[]{null, "", "../shop", "UPPER", "a".repeat(121)}) {
            assertNotFound(() -> service.shop(slug));
            assertNotFound(() -> service.category(slug));
        }
        verify(repository, never()).findPublicProduct(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void hiddenOrModeratedResourceIsIndistinguishableFromMissing() {
        when(repository.findPublicProduct(8L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.product(8L));
    }

    @Test
    void imageRequiresVisibilityAndOwnershipBeforeReadingServerKey() {
        when(repository.findPublicImageStorageKey(4L, 6L)).thenReturn(Optional.of("products/safe.png"));
        when(storage.read("products/safe.png")).thenReturn(new StoredContent(new byte[]{1, 2}, "image/png"));

        assertThat(service.image(4L, 6L).mediaType()).isEqualTo("image/png");
        verify(storage).read("products/safe.png");

        when(repository.findPublicImageStorageKey(4L, 7L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.image(4L, 7L));
        verify(storage, never()).read("products/missing.png");
    }

    @Test
    void missingStoredFileUsesSafeNotFoundContract() {
        when(repository.findPublicImageStorageKey(4L, 6L)).thenReturn(Optional.of("products/missing.png"));
        when(storage.read("products/missing.png"))
                .thenThrow(new StorageException(StorageException.Reason.IO_FAILURE));

        assertNotFound(() -> service.image(4L, 6L));
    }

    @Test
    void discoveryNormalizesCriteriaAndWhitelistsUnknownSortToNewest() {
        ProductSearchCriteria requested = new ProductSearchCriteria();
        requested.setQ("  Laptop  ");
        requested.setShop("  shop-a  ");
        requested.setSort("price desc; drop table products");
        requested.setPage(null);
        requested.setSize(null);
        when(repository.countPublicProducts(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        var page = service.search(requested);

        ArgumentCaptor<ProductSearchCriteria> criteria = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(repository).countPublicProducts(criteria.capture());
        assertThat(criteria.getValue().getQ()).isEqualTo("Laptop");
        assertThat(criteria.getValue().getShop()).isEqualTo("shop-a");
        assertThat(criteria.getValue().getSort()).isEqualTo("newest");
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(ProductSearchCriteria.DEFAULT_SIZE);
        verify(repository, never()).findPublicProducts(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void discoveryBuildsBoundedDeterministicPageMetadata() {
        ProductSearchCriteria requested = new ProductSearchCriteria();
        requested.setPage(1);
        requested.setSize(12);
        requested.setSort("priceAsc");
        when(repository.countPublicProducts(org.mockito.ArgumentMatchers.any())).thenReturn(25L);
        when(repository.findPublicProducts(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(ProductSort.PRICE_ASC),
                org.mockito.ArgumentMatchers.eq(12), org.mockito.ArgumentMatchers.eq(12L)))
                .thenReturn(List.of());

        var page = service.search(requested);

        assertThat(page.totalItems()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.criteria().getSort()).isEqualTo("priceAsc");
    }

    @Test
    void discoveryRejectsInvalidPriceAndUnboundedPageSizeAtServiceBoundary() {
        ProductSearchCriteria invalidPrice = new ProductSearchCriteria();
        invalidPrice.setMinPrice(new BigDecimal("1.5"));
        assertValidation(() -> service.search(invalidPrice));

        ProductSearchCriteria unbounded = new ProductSearchCriteria();
        unbounded.setSize(49);
        assertValidation(() -> service.search(unbounded));
        verify(repository, never()).countPublicProducts(org.mockito.ArgumentMatchers.any());
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static void assertValidation(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
