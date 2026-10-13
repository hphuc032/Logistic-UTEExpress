package com.uteexpress.order.dto;

import java.time.Instant;

/** Owned public evidence; no actor IDs, ownership claims or inferred receipt/refund. */
public record ReturnRequestView(Long id, ReturnRequestStatus status, String reason,
        Instant requestedAt, Instant decisionAt, String decisionNote, Long version) { }
