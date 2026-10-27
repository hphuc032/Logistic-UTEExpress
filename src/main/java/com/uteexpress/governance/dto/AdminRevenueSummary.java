package com.uteexpress.governance.dto;

import com.uteexpress.reporting.dto.RevenueSummary.Window;
import java.time.Instant;
public record AdminRevenueSummary(Long shopId, String timezone, Instant generatedAt, Window today, Window month, Window selected) { }
