package com.uteexpress.order.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.time.*;
import java.time.temporal.ChronoUnit;

public record RevenuePeriod(LocalDate from, LocalDate toExclusive) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public RevenuePeriod {
        if (from == null || toExclusive == null || from.getYear() < 1900 || toExclusive.getYear() > 9999
                || !toExclusive.isAfter(from) || ChronoUnit.DAYS.between(from, toExclusive) > 366)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }
    public Instant start() { return from.atStartOfDay(ZONE).toInstant(); }
    public Instant end() { return toExclusive.atStartOfDay(ZONE).toInstant(); }
}
