package com.uteexpress.governance;

import com.uteexpress.governance.dto.*;
import com.uteexpress.common.exception.ApplicationException;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class OpsReportFilterTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-12-31T18:00:00Z"), ZoneOffset.UTC);
    @Test void defaultWindowUsesLocalDateAndInclusiveEndAcrossYearBoundary() {
        var window = OpsReportWindow.resolve(null, null, clock);
        assertThat(window.from()).isEqualTo(LocalDate.parse("2026-12-03"));
        assertThat(window.to()).isEqualTo(LocalDate.parse("2027-01-01"));
        assertThat(window.start()).isEqualTo(Instant.parse("2026-12-02T17:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2027-01-01T17:00:00Z"));
    }
    @Test void exactly366DaysAllowedBut367AndReversedRangesRejected() {
        assertThat(OpsReportWindow.resolve(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"), clock)).isNotNull();
        assertThatThrownBy(() -> OpsReportWindow.resolve(LocalDate.parse("2024-01-01"), LocalDate.parse("2025-01-01"), clock)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> OpsReportWindow.resolve(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01"), clock)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> OpsReportWindow.resolve(LocalDate.MAX, LocalDate.MAX, clock)).isInstanceOf(ApplicationException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"' OR 1=1 --", "lowercase", "<script>", "A B"})
    void rejectsNonCodeAuditFilters(String value) {
        assertThatThrownBy(() -> new OpsAuditFilter(value, null, null, null, 0)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new OpsAuditFilter(null, value, null, null, 0)).isInstanceOf(ApplicationException.class);
    }
    @Test void auditFiltersNormalizeBlankButDoNotAcceptNegativeIdsOrUnboundedOffsets() {
        assertThat(new OpsAuditFilter("  ", " SHOP ", null, null, 0).action()).isNull();
        assertThat(new OpsAuditFilter(null, " SHOP ", null, null, 0).targetType()).isEqualTo("SHOP");
        assertThatThrownBy(() -> new OpsAuditFilter(null, null, -1L, null, 0)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new OpsAuditFilter(null, null, null, 0L, 0)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new OpsAuditFilter(null, null, null, null, 501)).isInstanceOf(ApplicationException.class);
    }
}
