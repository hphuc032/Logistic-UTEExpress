package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.*;
import com.uteexpress.governance.dto.OpsDashboardMetrics.StateTotals;
import com.uteexpress.governance.repository.OpsDashboardReadRepository;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import java.time.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
public class OpsDashboardReadService {
    private final OpsDashboardReadRepository reports;
    private final IdentityAuthenticationService identities;
    private final CurrentAccountIdProvider accounts;
    private final Clock clock;
    public OpsDashboardReadService(OpsDashboardReadRepository reports, IdentityAuthenticationService identities,
            CurrentAccountIdProvider accounts, Clock clock) {
        this.reports = reports; this.identities = identities; this.accounts = accounts; this.clock = clock;
    }
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public OpsDashboardMetrics admin(LocalDate from, LocalDate to, Long shopId) {
        requireRole("ADMIN"); return metrics(from, to, shopId, true);
    }
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public OpsDashboardMetrics manager(LocalDate from, LocalDate to, Long shopId) {
        requireRole("MANAGER"); return metrics(from, to, shopId, false);
    }
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public OpsAuditPage audit(LocalDate from, LocalDate to, OpsAuditFilter filter) {
        requireRole("ADMIN");
        if (filter == null) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return reports.audit(OpsReportWindow.resolve(from, to, clock), filter);
    }
    private OpsDashboardMetrics metrics(LocalDate from, LocalDate to, Long shopId, boolean admin) {
        if (shopId != null && shopId <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        var window = OpsReportWindow.resolve(from, to, clock);
        return new OpsDashboardMetrics(window, shopId, StateTotals.of(reports.orders(window, shopId)),
                StateTotals.of(reports.shipments(window, shopId)), StateTotals.of(reports.shops(window, shopId)), admin ? StateTotals.of(reports.accounts(window)) : null, reports.refunds(window, shopId));
    }
    private void requireRole(String role) {
        Long actor = accounts.currentAccountId().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        var account = identities.findByUserId(actor).orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
        if (!account.active() || !account.roleCodes().contains(role)) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }
}
