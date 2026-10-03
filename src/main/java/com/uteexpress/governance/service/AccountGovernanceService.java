package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AccountView;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.identity.dto.AccountGovernanceData;
import com.uteexpress.identity.service.IdentityAccountGovernanceService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AccountGovernanceService {
    private final IdentityAccountGovernanceService identities;
    private final AuditLogService audit;
    private final CurrentAccountIdProvider currentAccount;
    private final RoleGovernanceService roles;

    public AccountGovernanceService(IdentityAccountGovernanceService identities,
            AuditLogService audit, CurrentAccountIdProvider currentAccount, RoleGovernanceService roles) {
        this.identities = identities;
        this.audit = audit;
        this.currentAccount = currentAccount;
        this.roles = roles;
    }

    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    @Transactional(readOnly = true)
    public Page<AccountView> search(String query, int page) {
        return identities.search(query, page).map(AccountGovernanceService::view);
    }

    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    @Transactional(readOnly = true)
    public AccountView get(Long id) { return view(identities.get(id)); }

    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
    @Transactional
    public AccountView setLocked(Long id, Long expectedVersion, boolean locked) {
        Long actor = currentAccount.currentAccountId().filter(value -> value > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        if (locked && actor.equals(id)) throw new ApplicationException(ErrorCode.CONFLICT);
        if (locked) roles.guardLastAdminBeforeLock(id);
        AccountGovernanceData before = identities.get(id);
        AccountGovernanceData after = identities.setLocked(id, expectedVersion, locked);
        audit.append(new AuditEntry(actor, locked ? "ACCOUNT_LOCKED" : "ACCOUNT_UNLOCKED",
                "ACCOUNT", id, Map.of("status", before.status()),
                Map.of("status", after.status()), "ACCOUNT_GOVERNANCE"));
        return view(after);
    }

    private static AccountView view(AccountGovernanceData user) {
        return new AccountView(user.id(), user.username(), user.email(),
                user.fullName(), user.phone(), user.status(), user.version());
    }
}
