package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.entity.ProductImage;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/** Read-only image ordering foundation; upload and mutation remain deferred. */
public interface ProductImageRepository extends Repository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    long countByProductId(Long productId);

    ProductImage saveAndFlush(ProductImage image);

    void delete(ProductImage image);

    void flush();
}
