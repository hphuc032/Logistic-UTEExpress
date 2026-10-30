package com.uteexpress.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Internal payment evidence, never an HTTP write contract. Assignment belongs to the trusted caller. */
public record CodCollectionCommand(@NotNull @Positive Long orderId, @NotNull BigDecimal collectedAmount) { }
