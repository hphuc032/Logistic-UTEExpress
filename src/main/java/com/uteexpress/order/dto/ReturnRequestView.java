package com.uteexpress.order.dto;

import java.time.Instant;

/** Owned public evidence; actor identity remains internal. Receipt is vendor attestation. */
public record ReturnRequestView(Long id, ReturnRequestStatus status, String reason,
        Instant requestedAt, Instant decisionAt, String decisionNote, Long version,
        Instant receivedAt, String receiptReference) { }
