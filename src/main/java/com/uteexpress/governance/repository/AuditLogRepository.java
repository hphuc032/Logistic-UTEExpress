package com.uteexpress.governance.repository;

import com.uteexpress.governance.entity.AuditLog;
import org.springframework.data.repository.Repository;

/** Intentionally exposes append only; no application update/delete API. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog saveAndFlush(AuditLog entry);
}
