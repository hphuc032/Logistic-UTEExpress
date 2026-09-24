package com.uteexpress.catalog.entity;

import com.uteexpress.catalog.repository.ProductRepository;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProductModelTest {
    @Test
    void modelUsesScalarModuleIdsMoneyEnumAndOptimisticVersion() throws Exception {
        assertThat(Product.class.getDeclaredField("shopId").getType()).isEqualTo(Long.class);
        assertThat(Product.class.getDeclaredField("categoryId").getType()).isEqualTo(Long.class);
        assertThat(Product.class.getDeclaredField("price").getType()).isEqualTo(BigDecimal.class);
        assertThat(Product.class.getDeclaredField("status").getType()).isEqualTo(ProductStatus.class);
        assertThat(Product.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        assertThat(ProductImage.class.getDeclaredField("productId").getType()).isEqualTo(Long.class);
        assertThat(ProductStatus.values()).containsExactly(ProductStatus.ACTIVE, ProductStatus.HIDDEN);
    }

    @Test
    void productDoesNotExposeBroadSettersOrRepositoryDeleteMethods() {
        assertThat(Arrays.stream(Product.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
        assertThat(Arrays.stream(ProductRepository.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete"));
    }
}
