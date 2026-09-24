package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.entity.ProductImage;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Read-only image ordering foundation; upload and mutation remain deferred. */
public interface ProductImageRepository extends Repository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);
}
