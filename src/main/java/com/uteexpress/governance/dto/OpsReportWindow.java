package com.uteexpress.governance.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** Inclusive local dates become an exclusive UTC upper bound. */
public record OpsReportWindow(LocalDate from, LocalDate to, Instant start, Instant end, String timezone) {
    public static OpsReportWindow resolve(LocalDate from, LocalDate to, Clock clock) {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var last = to == null ? LocalDate.ofInstant(clock.instant(), zone) : to;
        try {
            var first = from == null ? last.minusDays(29) : from;
            if (first.getYear() < 1 || last.getYear() > 9999 || first.isAfter(last)
                    || ChronoUnit.DAYS.between(first, last) > 365) throw invalid();
            return new OpsReportWindow(first, last, first.atStartOfDay(zone).toInstant(),
                    last.plusDays(1).atStartOfDay(zone).toInstant(), zone.getId());
        } catch (DateTimeException | ArithmeticException invalid) { throw invalid(); }
    }
    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.INVALID_REQUEST); }
}
