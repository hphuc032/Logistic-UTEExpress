package com.uteexpress.governance.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.governance.dto.AdminRevenueSummary;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.reporting.dto.*;
import com.uteexpress.reporting.service.RevenueProjectionService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import java.time.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

@Service
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminRevenueService {
    private final ObjectProvider<RevenueProjectionService> projections;
    private final ObjectProvider<PlatformTransactionManager> managers;
    private final IdentityAuthenticationService identities;
    private final CurrentAccountIdProvider current;
    private final Clock clock;
    public AdminRevenueService(ObjectProvider<RevenueProjectionService> projections, ObjectProvider<PlatformTransactionManager> managers,
            IdentityAuthenticationService identities, CurrentAccountIdProvider current, Clock clock) {
        this.projections=projections; this.managers=managers; this.identities=identities; this.current=current; this.clock=clock;
    }
    public AdminRevenueSummary dashboard(LocalDate from, LocalDate toExclusive, Long shopId) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        if(shopId!=null && shopId<=0 || (from==null)!=(toExclusive==null)) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        Instant now=clock.instant(); LocalDate date=now.atZone(RevenuePeriod.ZONE).toLocalDate();
        var today=new RevenuePeriod(date,date.plusDays(1));
        var month=new RevenuePeriod(date.withDayOfMonth(1),date.withDayOfMonth(1).plusMonths(1));
        var selected=from==null ? month:new RevenuePeriod(from,toExclusive);
        Long actor=current.currentAccountId().filter(id->id>0).orElseThrow(()->new ApplicationException(ErrorCode.UNAUTHENTICATED));
        var tx=new TransactionTemplate(managers.getObject());tx.setReadOnly(true);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return tx.execute(status->{
            var account=identities.findByUserId(actor).orElseThrow(()->new ApplicationException(ErrorCode.ACCESS_DENIED));
            if(!account.active() || !account.roleCodes().contains("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
            var scope=shopId==null ? RevenueScope.allShops():RevenueScope.shop(shopId);
            var query=projections.getObject();
            var todayResult=new RevenueSummary.Window(today,query.metrics(scope,today));
            var monthResult=new RevenueSummary.Window(month,query.metrics(scope,month));
            var selectedResult=selected.equals(month) ? monthResult:selected.equals(today) ? todayResult:new RevenueSummary.Window(selected,query.metrics(scope,selected));
            return new AdminRevenueSummary(shopId,RevenuePeriod.ZONE.getId(),now,todayResult,monthResult,selectedResult);
        });
    }
}
