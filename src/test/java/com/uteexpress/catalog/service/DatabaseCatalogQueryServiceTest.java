package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.repository.CatalogReadRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseCatalogQueryServiceTest {
    private final CatalogReadRepository repository = mock(CatalogReadRepository.class);
    private final DatabaseCatalogQueryService service = new DatabaseCatalogQueryService(repository);

    @Test
    void rejectsNullAndInvalidIdsBeforeReadingDatabase() {
        for (Set<Long> ids : List.of(
                new LinkedHashSet<>(java.util.Arrays.asList(1L, null)),
                Set.of(0L), Set.of(-1L))) {
            assertThatThrownBy(() -> service.requirePurchasableProducts(ids))
                    .isInstanceOfSatisfying(ApplicationException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        }
        assertThatThrownBy(() -> service.requirePurchasableProducts(null))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(repository);
    }

    @Test
    void emptyInputReturnsImmutableEmptyListWithoutDatabaseRead() {
        List<ProductSnapshot> result = service.requirePurchasableProducts(Set.of());
        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add(snapshot(1L))).isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void requiresEveryRequestedProductAndReturnsImmutableSnapshots() {
        Set<Long> requested = new LinkedHashSet<>(List.of(2L, 1L));
        Set<Long> ordered = new java.util.TreeSet<>(requested);
        when(repository.findPurchasableByIds(ordered)).thenReturn(List.of(snapshot(1L), snapshot(2L)));

        List<ProductSnapshot> result = service.requirePurchasableProducts(requested);

        assertThat(result).extracting(ProductSnapshot::productId).containsExactly(1L, 2L);
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findPurchasableByIds(ordered);
    }

    @Test
    void unavailableMemberRejectsWholeBatchWithoutPartialResult() {
        Set<Long> requested = Set.of(1L, 2L);
        when(repository.findPurchasableByIds(new java.util.TreeSet<>(requested)))
                .thenReturn(List.of(snapshot(1L)));

        assertThatThrownBy(() -> service.requirePurchasableProducts(requested))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static ProductSnapshot snapshot(Long id) {
        return new ProductSnapshot(id, 10L, "Product " + id, new BigDecimal("125000.00"), 0L);
    }
}
