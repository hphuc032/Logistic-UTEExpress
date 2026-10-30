package com.uteexpress.governance.dto;

import java.time.Instant;
import java.util.List;

public record OpsAuditPage(OpsReportWindow window, OpsAuditFilter filter, int size,
        long totalElements, List<Entry> content, boolean hasPrevious, boolean hasNext) {
    public OpsAuditPage { content = List.copyOf(content); }
    /** Audit summaries and account contact/credentials are deliberately not projected. */
    public record Entry(long id, Long actorId, String action, String targetType, long targetId,
            String reason, Instant createdAt) {}
}
