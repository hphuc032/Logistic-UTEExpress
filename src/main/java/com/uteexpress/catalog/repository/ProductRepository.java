package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.entity.Product;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Narrow persistence foundation; product mutations remain VENDOR-02 scope. */
public interface ProductRepository extends Repository<Product, Long> {
    Optional<Product> findById(Long id);
}
