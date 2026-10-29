package com.uteexpress.security.dto;

public record RequestBudgetDecision(boolean allowed, long retryAfterSeconds) { }
