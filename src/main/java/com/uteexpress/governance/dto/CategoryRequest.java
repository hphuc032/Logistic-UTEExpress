package com.uteexpress.governance.dto;

import jakarta.validation.constraints.*;

public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @PositiveOrZero Long version) {
    public CategoryRequest {
        name = name == null ? null : name.strip();
        slug = slug == null ? null : slug.strip();
    }
}
