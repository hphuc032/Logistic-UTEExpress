package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.entity.AuditLog;
import com.uteexpress.governance.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class AuditLogService {
    private static final Set<String> ROLES = Set.of("USER", "VENDOR", "MANAGER", "ADMIN", "SHIPPER");
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) { this.repository = repository; }

    /**
     * Caller authorizes its business action, resolves actorId server-side and supplies safe codes.
     * Mandatory propagation prevents a successful audit surviving a failed business transaction.
     * No HTTP endpoint exposes this command. Do not catch and ignore audit persistence failures.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long append(AuditEntry entry) {
        if (entry == null || entry.targetId() == null || entry.targetId() <= 0
                || (entry.actorId() != null && entry.actorId() <= 0)) {
            throw invalid();
        }
        requireCode(entry.action());
        requireCode(entry.targetType());
        requireCode(entry.reason());
        String before = summarize(entry.beforeState());
        String after = summarize(entry.afterState());
        AuditLog saved = repository.saveAndFlush(new AuditLog(entry.actorId(), entry.action(),
                entry.targetType(), entry.targetId(), before, after, entry.reason(), Instant.now()));
        return saved.getId();
    }

    private static String summarize(Map<String, String> state) {
        state.forEach((key, value) -> {
            boolean valid = switch (key) {
                case "status" -> value.matches("[A-Z][A-Z_]{0,39}");
                case "active" -> value.equals("true") || value.equals("false");
                case "role" -> ROLES.contains(value);
                case "ratePercent" -> value.matches("(0|[1-9][0-9]{0,2})(\\.[0-9]{1,4})?")
                        && new BigDecimal(value).compareTo(new BigDecimal("100")) <= 0;
                case "relatedId" -> validLong(value, false);
                case "version" -> validLong(value, true);
                default -> false;
            };
            if (!valid) { throw invalid(); }
        });
        return new TreeMap<>(state).entrySet().stream()
                .map(field -> field.getKey() + "=" + field.getValue())
                .collect(Collectors.joining(";"));
    }

    private static boolean validLong(String value, boolean allowZero) {
        if (!value.matches("[0-9]{1,19}")) { return false; }
        try {
            long parsed = Long.parseLong(value);
            return allowZero ? parsed >= 0 : parsed > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void requireCode(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) { throw invalid(); }
    }

    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }
}
