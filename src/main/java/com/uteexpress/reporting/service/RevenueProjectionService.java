package com.uteexpress.reporting.service;

import com.uteexpress.reporting.dto.*;
import com.uteexpress.reporting.repository.RevenueQueryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Internal read contract. Caller owns persisted authorization/scope and the reporting snapshot. */
@Service
@Transactional(propagation=Propagation.MANDATORY, readOnly=true)
public class RevenueProjectionService {
    private final ObjectProvider<RevenueQueryRepository> queries;
    public RevenueProjectionService(ObjectProvider<RevenueQueryRepository> queries) { this.queries=queries; }
    public RevenueMetrics metrics(long shop, RevenuePeriod period) { return metrics(RevenueScope.shop(shop),period); }
    public RevenueMetrics metrics(RevenueScope scope, RevenuePeriod period) { requireSnapshot(); return queries.getObject().metrics(scope,period); }
    public RevenueSummary.TopProducts topProducts(long shop, RevenuePeriod period) { requireSnapshot(); return queries.getObject().topProducts(shop,period); }
    private static void requireSnapshot() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ).equals(
                    org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new org.springframework.transaction.IllegalTransactionStateException("Reporting requires caller read-only REPEATABLE_READ snapshot");
    }
    // Exact production SQL exposed for acceptance EXPLAIN/query-count checks, never client supplied.
    public static final String EVIDENCE_SQL=RevenueQueryRepository.EVIDENCE_SQL;
    public static final String METRICS_SQL=RevenueQueryRepository.METRICS_SQL;
    public static final String GLOBAL_METRICS_SQL=RevenueQueryRepository.GLOBAL_METRICS_SQL;
    public static final String TOP_SQL=RevenueQueryRepository.TOP_SQL;
    public static MapSqlParameterSource parameters(long shop, RevenuePeriod period) { return RevenueQueryRepository.parameters(shop,period); }
}
