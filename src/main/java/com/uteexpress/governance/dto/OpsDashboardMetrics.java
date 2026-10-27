package com.uteexpress.governance.dto;

import java.util.List;

public record OpsDashboardMetrics(OpsReportWindow window, Long shopId,
        StateTotals orders, StateTotals shipments, StateTotals shops, StateTotals accounts, long succeededRefunds) {
    public record StateCount(String status, long count) {}
    public record StateTotals(long total, List<StateCount> states) {
        public StateTotals { states = List.copyOf(states); }
        public static StateTotals of(List<StateCount> states) {
            return new StateTotals(states.stream().mapToLong(StateCount::count).sum(), states);
        }
    }
}
