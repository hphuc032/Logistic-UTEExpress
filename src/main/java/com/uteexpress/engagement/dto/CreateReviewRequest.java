package com.uteexpress.engagement.dto;
import jakarta.validation.constraints.*;
public record CreateReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @NotBlank String comment) { }
