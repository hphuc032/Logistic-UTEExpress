package com.uteexpress.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Inventory batches require distinct product IDs and at least one entry. */
public record StockQuantity(@NotNull @Positive Long productId, @Positive int quantity) { }
