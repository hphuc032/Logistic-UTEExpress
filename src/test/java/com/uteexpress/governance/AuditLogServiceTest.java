package com.uteexpress.governance;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.repository.AuditLogRepository;
import com.uteexpress.governance.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {
    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogService service = new AuditLogService(repository);

    @ParameterizedTest
    @ValueSource(strings = {"password", "passwordHash", "otp", "jwt", "email", "address", "requestBody"})
    void rejectsUnapprovedSnapshotFields(String field) {
        var entry = new AuditEntry(1L, "CATEGORY_UPDATED", "CATEGORY", 2L,
                Map.of(), Map.of(field, "must-not-persist"), "CONFIGURATION_CHANGE");
        assertThatThrownBy(() -> service.append(entry)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsFreeTextReasonsAndInvalidIds() {
        assertThatThrownBy(() -> service.append(new AuditEntry(1L, "UPDATED", "CATEGORY", 2L,
                Map.of(), Map.of(), "Contact someone@example.com"))).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.append(new AuditEntry(0L, "UPDATED", "CATEGORY", 2L,
                Map.of(), Map.of(), "CONFIGURATION_CHANGE"))).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.append(new AuditEntry(1L, "UPDATED", "CATEGORY", -1L,
                Map.of(), Map.of(), "CONFIGURATION_CHANGE"))).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void validatesSnapshotValuesWithoutEchoingThePayload() {
        for (var state : java.util.List.of(Map.of("active", "yes"), Map.of("role", "ROOT"),
                Map.of("ratePercent", "100.1"), Map.of("status", "<script>"),
                Map.of("relatedId", "9999999999999999999"))) {
            assertThatThrownBy(() -> service.append(new AuditEntry(1L, "UPDATED", "CATEGORY", 2L,
                    Map.of(), state, "CONFIGURATION_CHANGE")))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessage("Request validation failed.");
        }
        verifyNoInteractions(repository);
    }
}
