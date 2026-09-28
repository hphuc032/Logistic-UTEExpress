package com.uteexpress.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductView(Long id, String name, String description, BigDecimal price, int stock,
        String status, Long categoryId, String categoryName, boolean categoryActive,
        Long version, List<ProductImageView> images) { }
