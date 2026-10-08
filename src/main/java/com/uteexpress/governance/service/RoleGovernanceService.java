package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.dto.ManagedRole;
import com.uteexpress.governance.repository.RoleGovernanceRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoleGovernanceService {
    private final RoleGovernanceRepository roles;
    private final CurrentAccountIdProvider currentAccount;
    private final AuditLogService audit;

    public RoleGovernanceService(RoleGovernanceRepository roles, CurrentAccountIdProvider currentAccount,
            AuditLogService audit) {
        this.roles = roles;
        this.currentAccount = currentAccount;
        this.audit = audit;
    }

    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    @Transactional(readOnly = true)
    public Set<String> rolesFor(Long accountId) {
        if (accountId == null || accountId <= 0) throw invalid();
        return roles.rolesFor(accountId);
    }

    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    @Transactional(readOnly = true)
    public List<RoleGovernanceRepository.ActiveShipper> activeShippers() {
        return roles.activeShippers();
    }

    /** Serialize assignment with SHIPPER grants/revocations and account locks. */
    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    @Transactional
    public void requireActiveShipperForUpdate(Long accountId) {
        if (accountId == null || accountId <= 0) throw invalid();
        roles.lockRole(ManagedRole.SHIPPER.name());
        RoleGovernanceRepository.AccountState target = roles.lockAccount(accountId);
        if (target == null || !"ACTIVE".equals(target.status())
                || !roles.rolesFor(accountId).contains(ManagedRole.SHIPPER.name())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
    }

    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
    @Transactional
    public void change(Long accountId, Long expectedVersion, ManagedRole role, boolean grant) {
        if (accountId == null || accountId <= 0 || expectedVersion == null || expectedVersion < 0
                || role == null) throw invalid();
        long actor = currentAccount.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        long roleId = roles.lockRole(role.name());
        RoleGovernanceRepository.AccountState target = roles.lockAccount(accountId);
        if (target == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        if (target.version() != expectedVersion || !"ACTIVE".equals(target.status()))
            throw new ApplicationException(ErrorCode.CONFLICT);
        if (!grant && role == ManagedRole.ADMIN && roles.activeAdminCount() <= 1)
            throw new ApplicationException(ErrorCode.CONFLICT);
        int changed = grant ? roles.assign(accountId, roleId) : roles.revoke(accountId, roleId);
        if (changed != 1 || roles.rotateToken(accountId, expectedVersion) != 1)
            throw new ApplicationException(ErrorCode.CONFLICT);
        audit.append(new AuditEntry(actor, grant ? "ROLE_GRANTED" : "ROLE_REVOKED", "ACCOUNT",
                accountId, grant ? Map.of() : Map.of("role", role.name()),
                grant ? Map.of("role", role.name()) : Map.of(), "ROLE_GOVERNANCE"));
    }

    /** Must run before locking an ADMIN account, in the same transaction. */
    @Transactional
    public void guardLastAdminBeforeLock(Long accountId) {
        roles.lockRole(ManagedRole.ADMIN.name());
        if (roles.rolesFor(accountId).contains(ManagedRole.ADMIN.name()) && roles.activeAdminCount() <= 1)
            throw new ApplicationException(ErrorCode.CONFLICT);
    }

    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }
}
