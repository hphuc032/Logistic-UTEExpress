package com.uteexpress.governance.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;

public record OpsAuditFilter(String action, String targetType, Long actorId, Long targetId, int page) {
    public OpsAuditFilter {
        action = code(action); targetType = code(targetType);
        if (page < 0 || page > 500 || actorId != null && actorId <= 0 || targetId != null && targetId <= 0) throw invalid();
    }
    private static String code(String value) {
        if (value == null || value.isBlank()) return null;
        value = value.strip();
        if (!value.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalid();
        return value;
    }
    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.INVALID_REQUEST); }
}
