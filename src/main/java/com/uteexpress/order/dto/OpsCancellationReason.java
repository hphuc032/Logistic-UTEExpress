package com.uteexpress.order.dto;

/** Provisional names for HP review; callers cannot supply a history description. */
public enum OpsCancellationReason {
    UNABLE_TO_FULFILL("Ops cancellation: unable to fulfill");

    private final String description;

    OpsCancellationReason(String description) { this.description = description; }

    public String description() { return description; }
}
