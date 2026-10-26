package com.uteexpress.order.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.repository.RevenueQueryRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.service.VendorShopQueryService;
import java.time.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

@Service
@PreAuthorize("hasAuthority('ROLE_VENDOR')")
public class RevenueService {
    private final ObjectProvider<RevenueQueryRepository> queries;
    private final ObjectProvider<PlatformTransactionManager> managers;
    private final CurrentAccountIdProvider current;
    private final IdentityAuthenticationService identities;
    private final VendorShopQueryService shops;
    private final Clock clock;
    public RevenueService(ObjectProvider<RevenueQueryRepository> queries, ObjectProvider<PlatformTransactionManager> managers,
            CurrentAccountIdProvider current, IdentityAuthenticationService identities, VendorShopQueryService shops, Clock clock) {
        this.queries=queries; this.managers=managers; this.current=current;
        this.identities=identities; this.shops=shops; this.clock=clock;
    }
    public RevenueSummary dashboard(LocalDate from, LocalDate toExclusive) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        Instant now=clock.instant(); LocalDate date=now.atZone(RevenuePeriod.ZONE).toLocalDate();
        var today=new RevenuePeriod(date,date.plusDays(1));
        var month=new RevenuePeriod(date.withDayOfMonth(1),date.withDayOfMonth(1).plusMonths(1));
        if((from==null)!=(toExclusive==null)) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        var selected=from==null ? month : new RevenuePeriod(from,toExclusive);
        Long actor=current.currentAccountId().filter(id -> id>0).orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        var tx=new TransactionTemplate(managers.getObject()); tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return tx.execute(status -> {
            var account=identities.findByUserId(actor).orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
            if(!account.active() || !account.roleCodes().contains("VENDOR")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
            var shop=shops.requireApprovedOwnedShop(actor); var query=queries.getObject();
            var todayResult=new RevenueSummary.Window(today,query.metrics(shop.shopId(),today));
            var monthResult=new RevenueSummary.Window(month,query.metrics(shop.shopId(),month));
            var selectedResult=selected.equals(month) ? monthResult : selected.equals(today) ? todayResult
                : new RevenueSummary.Window(selected,query.metrics(shop.shopId(),selected));
            return new RevenueSummary(shop.shopName(),RevenuePeriod.ZONE.getId(),now,todayResult,monthResult,selectedResult,
                query.topProducts(shop.shopId(),selected));
        });
    }
}
