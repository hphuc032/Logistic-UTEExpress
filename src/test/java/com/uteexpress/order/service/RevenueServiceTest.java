package com.uteexpress.order.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.repository.RevenueQueryRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.dto.VendorShopData;
import com.uteexpress.shop.service.VendorShopQueryService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RevenueServiceTest {
    final RevenueQueryRepository queries=mock(RevenueQueryRepository.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final CurrentAccountIdProvider current=mock(CurrentAccountIdProvider.class);
    final IdentityAuthenticationService identities=mock(IdentityAuthenticationService.class);
    final VendorShopQueryService shops=mock(VendorShopQueryService.class);
    final Clock clock=mock(Clock.class);
    final StaticListableBeanFactory beans=new StaticListableBeanFactory(Map.of("queries",queries,"manager",manager));
    final RevenueService service=new RevenueService(beans.getBeanProvider(RevenueQueryRepository.class),
        beans.getBeanProvider(PlatformTransactionManager.class),current,identities,shops,clock);
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-12-31T17:00:00Z"));
        when(current.currentAccountId()).thenReturn(Optional.of(7L));
        when(identities.findByUserId(7L)).thenReturn(Optional.of(new AuthAccountSnapshot(7L,"display",null,true,0L,Set.of("VENDOR"))));
        when(shops.requireApprovedOwnedShop(7L)).thenReturn(new VendorShopData(19L,"Owned"));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var zero=new RevenueAmount(BigDecimal.ZERO,0);
        when(queries.metrics(eq(19L),any())).thenReturn(new RevenueMetrics(zero,zero,zero,zero,zero,0,0,0));
        when(queries.topProducts(eq(19L),any())).thenReturn(new RevenueSummary.TopProducts(List.of(),0));
    }
    @Test void newYearBoundariesAndOneClockReadUseTrustedScopeAndReadOnlyRepeatableRead() {
        var result=service.dashboard(null,null);
        assertThat(result.today().period().start()).isEqualTo(Instant.parse("2026-12-31T17:00:00Z"));
        assertThat(result.month().period().end()).isEqualTo(Instant.parse("2027-01-31T17:00:00Z"));
        assertThat(result.selected()).isEqualTo(result.month());
        verify(clock,times(1)).instant();
        verify(manager).getTransaction(argThat(d -> d.isReadOnly() && d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
        verify(queries,times(2)).metrics(eq(19L),any());
    }
    @ParameterizedTest @ValueSource(strings={"reversed","partial","wide","ancient"})
    void rejectsInvalidPeriodsBeforeReadingPersistence(String kind) {
        LocalDate start=LocalDate.of(2026,1,1),end=start.plusDays(1);
        switch(kind) { case "reversed" -> end=start; case "partial" -> end=null;
            case "wide" -> end=start.plusDays(367); case "ancient" -> start=LocalDate.of(1899,12,31); }
        var from=start; var to=end;
        assertThatThrownBy(() -> service.dashboard(from,to)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(manager,identities,queries);
    }
    @ParameterizedTest @ValueSource(strings={"missing","locked","revoked"})
    void missingOrRevokedPersistedVendorCannotReadQueries(String kind) {
        when(identities.findByUserId(7L)).thenReturn(kind.equals("missing") ? Optional.empty()
            : Optional.of(new AuthAccountSnapshot(7L,"display",null,!kind.equals("locked"),0L,
                kind.equals("revoked") ? Set.of("USER") : Set.of("VENDOR"))));
        assertThatThrownBy(() -> service.dashboard(null,null)).isInstanceOfSatisfying(ApplicationException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        verifyNoInteractions(queries,shops);
    }
    @Test void noPrincipalFailsWithoutPersistence() {
        when(current.currentAccountId()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.dashboard(null,null)).isInstanceOfSatisfying(ApplicationException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(manager,queries);
    }
    @Test void existingTransactionIsRejectedRatherThanJoiningWrongSnapshot() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(() -> service.dashboard(null,null)).isInstanceOf(ApplicationException.class); }
        finally { TransactionSynchronizationManager.clear(); }
        verifyNoInteractions(manager,current,queries);
    }
    @Test void unknownComponentMakesDerivedTotalsUnavailableButKnownZeroStaysKnown() {
        var unknown=new RevenueAmount(new BigDecimal("90000"),1);
        var zero=new RevenueAmount(BigDecimal.ZERO,0);
        var metrics=new RevenueMetrics(zero,zero,unknown,zero,unknown,0,0,0);
        assertThat(unknown.value()).isNull(); assertThat(unknown.complete()).isFalse();
        assertThat(metrics.netVendorSales().value()).isNull(); assertThat(metrics.netCommission().value()).isNull();
        assertThat(zero.value()).isEqualByComparingTo("0"); assertThat(zero.complete()).isTrue();
    }
    @Test void adjustmentsCanProduceNegativePeriodTotals() {
        var zero=new RevenueAmount(BigDecimal.ZERO,0);
        var metrics=new RevenueMetrics(zero,zero,new RevenueAmount(new BigDecimal("90000"),0),zero,
            new RevenueAmount(new BigDecimal("9000"),0),0,0,0);
        assertThat(metrics.netVendorSales().value()).isEqualByComparingTo("-90000");
        assertThat(metrics.netCommission().value()).isEqualByComparingTo("-9000");
    }
    @Test void unknownCollectionDoesNotReplaceIndependentDeliveredSalesNetFormula() {
        var metrics=new RevenueMetrics(new RevenueAmount(new BigDecimal("90000"),0),
            new RevenueAmount(null,1),new RevenueAmount(new BigDecimal("10000"),0),
            new RevenueAmount(new BigDecimal("9000"),0),new RevenueAmount(new BigDecimal("1000"),0),0,0,0);
        assertThat(metrics.collectedPayments().complete()).isFalse();
        assertThat(metrics.collectedPayments().value()).isNull();
        assertThat(metrics.netVendorSales().complete()).isTrue();
        assertThat(metrics.netVendorSales().value()).isEqualByComparingTo("80000");
        assertThat(metrics.netCommission().value()).isEqualByComparingTo("8000");
    }
}
