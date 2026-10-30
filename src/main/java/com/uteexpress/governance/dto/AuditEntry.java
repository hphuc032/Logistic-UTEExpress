package com.uteexpress.governance.dto;

import java.util.Map;

/**
 * Internal service command, never bind from an HTTP request.
 * actorId must come from a verified identity service; null means a documented system action.
 * action/targetType/reason are server-defined codes, not user text.
 * Snapshot keys: status, active, role, ratePercent, relatedId, version.
 */
public record AuditEntry(Long actorId, String action, String targetType, Long targetId,
        Map<String, String> beforeState, Map<String, String> afterState, String reason) {
    public AuditEntry {
        beforeState = beforeState == null ? Map.of() : Map.copyOf(beforeState);
        afterState = afterState == null ? Map.of() : Map.copyOf(afterState);
    }
}
