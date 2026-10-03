package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.repository.CommissionPolicyRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class CommissionPolicyManagementService {
    private final CommissionPolicyRepository policies;
    private final AuditLogService audit;
    private final CurrentAccountIdProvider accounts;

    public CommissionPolicyManagementService(CommissionPolicyRepository policies,
            AuditLogService audit, CurrentAccountIdProvider accounts) {
        this.policies = policies;
        this.audit = audit;
        this.accounts = accounts;
    }

    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    @Transactional(readOnly = true)
    public List<CommissionPolicyRepository.PolicyRow> list() {
        return policies.list();
    }

    // Manager write limits are not yet defined by the shared contract; only ADMIN may set a rate.
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
    @Transactional
    public Long create(BigDecimal ratePercent, Instant effectiveFrom) {
        if (ratePercent == null || ratePercent.signum() < 0
                || ratePercent.compareTo(new BigDecimal("100")) > 0 || ratePercent.scale() > 4
                || effectiveFrom == null || effectiveFrom.getNano() % 1000 != 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        Long actor = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        Long id;
        try {
            id = policies.create(ratePercent, effectiveFrom, actor);
        } catch (DataIntegrityViolationException conflict) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        audit.append(new AuditEntry(actor, "COMMISSION_POLICY_CREATED", "COMMISSION_POLICY", id,
                Map.of(), Map.of("ratePercent", ratePercent.toPlainString(), "active", "true"),
                "COMMISSION_CONFIGURATION"));
        return id;
    }
}
