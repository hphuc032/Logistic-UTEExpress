package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductCreateRequest;
import com.uteexpress.catalog.dto.ProductUpdateRequest;
import com.uteexpress.catalog.entity.Product;
import com.uteexpress.catalog.entity.ProductImage;
import com.uteexpress.catalog.entity.ProductStatus;
import com.uteexpress.catalog.repository.ProductImageRepository;
import com.uteexpress.catalog.repository.ProductRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.FileStorageService;
import com.uteexpress.common.storage.StoredFile;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.governance.dto.CategoryOption;
import com.uteexpress.governance.service.CategoryQueryService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.dto.VendorShopData;
import com.uteexpress.shop.service.VendorShopQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {
    ProductRepository products = mock(ProductRepository.class);
    ProductImageRepository images = mock(ProductImageRepository.class);
    CurrentAccountIdProvider accounts = mock(CurrentAccountIdProvider.class);
    VendorShopQueryService shops = mock(VendorShopQueryService.class);
    CategoryQueryService categories = mock(CategoryQueryService.class);
    FileStorageService storage = mock(FileStorageService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC);
    ProductService service = new ProductService(products, images, accounts, shops, categories, storage, clock);

    @BeforeEach
    void fixture() {
        when(accounts.currentAccountId()).thenReturn(Optional.of(7L));
        when(shops.requireApprovedOwnedShop(7L)).thenReturn(new VendorShopData(70L, "Shop"));
        when(categories.requireActiveCategory(5L)).thenReturn(new CategoryOption(5L, "Category", true));
        when(images.findByProductIdOrderByPositionAsc(any())).thenReturn(List.of());
        when(products.saveAndFlush(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0), 11L, 0L));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createUsesServerOwnedApprovedShopAndActiveCategory() {
        var result = service.create(createRequest());

        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        var product = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(products).saveAndFlush(product.capture());
        assertThat(product.getValue().getShopId()).isEqualTo(70L);
        assertThat(product.getValue().getCategoryId()).isEqualTo(5L);
        assertThat(product.getValue().getPrice()).isEqualByComparingTo("150000");
    }

    @Test
    void updatePreservesOwnerAndRejectsStaleVersion() {
        Product product = persisted(Product.create(70L, 5L, "Old", null,
                new BigDecimal("100"), 3, Instant.now(clock)), 12L, 4L);
        when(products.findByIdAndShopId(12L, 70L)).thenReturn(Optional.of(product));
        ProductUpdateRequest request = updateRequest(3L);

        assertThatThrownBy(() -> service.update(12L, request))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(products, never()).saveAndFlush(product);
        assertThat(product.getShopId()).isEqualTo(70L);
    }

    @Test
    void softDeleteHidesWithoutRepositoryDelete() {
        Product product = persisted(Product.create(70L, 5L, "Product", null,
                new BigDecimal("100"), 3, Instant.now(clock)), 12L, 2L);
        when(products.findByIdAndShopId(12L, 70L)).thenReturn(Optional.of(product));

        service.hide(12L, 2L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        verify(products).saveAndFlush(product);
    }

    @Test
    void uploadLimitIsCheckedBeforeWritingFile() {
        Product product = persisted(Product.create(70L, 5L, "Product", null,
                new BigDecimal("100"), 3, Instant.now(clock)), 12L, 0L);
        when(products.findByIdAndShopIdForUpdate(12L, 70L)).thenReturn(Optional.of(product));
        when(images.findByProductIdOrderByPositionAsc(12L)).thenReturn(List.of(
                image(1), image(2), image(3), image(4), image(5)));

        assertThatThrownBy(() -> service.addImage(12L, upload(), null))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(storage, never()).storeImage(any(), any(), any());
    }

    @Test
    void failedImagePersistenceDeletesNewFileAfterRollback() {
        Product product = persisted(Product.create(70L, 5L, "Product", null,
                new BigDecimal("100"), 3, Instant.now(clock)), 12L, 0L);
        when(products.findByIdAndShopIdForUpdate(12L, 70L)).thenReturn(Optional.of(product));
        when(storage.storeImage(any(), any(), any())).thenReturn(new StoredFile("products/00000000-0000-0000-0000-000000000001.png", "image/png", 1, 1));
        when(images.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("conflict"));
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> service.addImage(12L, upload(), "Alt"))
                .isInstanceOf(ApplicationException.class);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete("products/00000000-0000-0000-0000-000000000001.png");
    }

    @Test
    void imageFileIsDeletedOnlyAfterDatabaseCommit() {
        Product product = persisted(Product.create(70L, 5L, "Product", null,
                new BigDecimal("100"), 3, Instant.now(clock)), 12L, 0L);
        ProductImage image = ProductImage.create(12L,
                "products/00000000-0000-0000-0000-000000000001.png", 0, null);
        ReflectionTestUtils.setField(image, "id", 9L);
        when(products.findByIdAndShopIdForUpdate(12L, 70L)).thenReturn(Optional.of(product));
        when(images.findByIdAndProductId(9L, 12L)).thenReturn(Optional.of(image));
        TransactionSynchronizationManager.initSynchronization();

        service.deleteImage(12L, 9L);
        verify(storage, never()).delete(any());
        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage).delete(image.getStorageKey());
    }

    private void complete(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(synchronization -> {
            if (status == TransactionSynchronization.STATUS_COMMITTED) synchronization.afterCommit();
            synchronization.afterCompletion(status);
        });
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static Product persisted(Product product, long id, long version) {
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "version", version);
        return product;
    }

    private static ProductImage image(int position) {
        return ProductImage.create(12L, "products/00000000-0000-0000-0000-00000000000" + position + ".png", position, null);
    }

    private static UploadContent upload() {
        return new UploadContent(new byte[]{1}, "image.png", "image/png");
    }

    private static ProductCreateRequest createRequest() {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName(" Product ");
        request.setDescription(" Description ");
        request.setPrice(new BigDecimal("150000"));
        request.setStock(4);
        request.setCategoryId(5L);
        return request;
    }

    private static ProductUpdateRequest updateRequest(long version) {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("Updated");
        request.setDescription("Description");
        request.setPrice(new BigDecimal("200000"));
        request.setStock(9);
        request.setCategoryId(5L);
        request.setVersion(version);
        return request;
    }
}
