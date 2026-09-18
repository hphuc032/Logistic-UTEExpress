package com.uteexpress.common.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(Instant timestamp, int status, String code,
                            String message, String path, List<Violation> errors) {
    public ErrorResponse {
        errors = List.copyOf(errors);
    }

    public record Violation(String field, String message) {
    }
}
