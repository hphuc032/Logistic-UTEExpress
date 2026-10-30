package com.uteexpress.governance.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** QD policy projection. ratePercent is 0..100; precision awaits TD/QD agreement. */
public record CommissionPolicySnapshot(Long policyId, BigDecimal ratePercent, Instant effectiveFrom) { }
