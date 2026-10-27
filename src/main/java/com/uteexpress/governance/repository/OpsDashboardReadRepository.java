package com.uteexpress.governance.repository;

import com.uteexpress.governance.dto.*;
import com.uteexpress.governance.dto.OpsDashboardMetrics.StateCount;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/** Fixed-grain read projections. No item joins, mutation, or row-locking statements. */
@Repository
public class OpsDashboardReadRepository {
    public static final int AUDIT_SIZE = 20;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    public OpsDashboardReadRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) { this.jdbc = jdbc; }

    public List<StateCount> orders(OpsReportWindow window, Long shopId) {
        return grouped("SELECT o.status, count(*) AS n FROM uteexpress.orders o WHERE o.created_at >= :start AND o.created_at < :end"
                + (shopId == null ? "" : " AND o.shop_id=:shop") + " GROUP BY o.status ORDER BY o.status", params(window).addValue("shop", shopId));
    }
    public List<StateCount> shipments(OpsReportWindow window, Long shopId) {
        return grouped("SELECT s.status, count(*) AS n FROM uteexpress.shipments s JOIN uteexpress.orders o ON o.id=s.order_id WHERE s.created_at >= :start AND s.created_at < :end"
                + (shopId == null ? "" : " AND o.shop_id=:shop") + " GROUP BY s.status ORDER BY s.status", params(window).addValue("shop", shopId));
    }
    public List<StateCount> shops(OpsReportWindow window, Long shopId) {
        return grouped("SELECT s.status, count(*) AS n FROM uteexpress.shops s WHERE s.created_at >= :start AND s.created_at < :end"
                + (shopId == null ? "" : " AND s.id=:shop") + " GROUP BY s.status ORDER BY s.status", params(window).addValue("shop", shopId));
    }
    /** Admin only: global account creation counts; the Shop filter does not imply account ownership. */
    public List<StateCount> accounts(OpsReportWindow window) {
        return grouped("SELECT u.status, count(*) AS n FROM uteexpress.users u WHERE u.created_at >= :start AND u.created_at < :end GROUP BY u.status ORDER BY u.status", params(window));
    }
    public long refunds(OpsReportWindow window, Long shopId) {
        return jdbc.getObject().queryForObject("SELECT count(*) FROM uteexpress.refunds r JOIN uteexpress.orders o ON o.id=r.order_id WHERE r.status='SUCCEEDED' AND r.server_timestamp >= :start AND r.server_timestamp < :end"
                + (shopId == null ? "" : " AND o.shop_id=:shop"), params(window).addValue("shop", shopId), Long.class);
    }
    private List<StateCount> grouped(String sql, MapSqlParameterSource params) {
        return jdbc.getObject().query(sql, params, (rs, ignored) -> new StateCount(rs.getString("status"), rs.getLong("n")));
    }
    public OpsAuditPage audit(OpsReportWindow window, OpsAuditFilter filter) {
        String where = " WHERE created_at >= :start AND created_at < :end";
        var parameters = params(window);
        if (filter.action() != null) { where += " AND action=:action"; parameters.addValue("action", filter.action()); }
        if (filter.targetType() != null) { where += " AND target_type=:type"; parameters.addValue("type", filter.targetType()); }
        if (filter.actorId() != null) { where += " AND actor_id=:actor"; parameters.addValue("actor", filter.actorId()); }
        if (filter.targetId() != null) { where += " AND target_id=:target"; parameters.addValue("target", filter.targetId()); }
        long count = jdbc.getObject().queryForObject("SELECT count(*) FROM uteexpress.audit_logs" + where, parameters, Long.class);
        var rows = jdbc.getObject().query("SELECT id,actor_id,action,target_type,target_id,reason,created_at FROM uteexpress.audit_logs" + where
                + " ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset",
                parameters.addValue("limit", AUDIT_SIZE).addValue("offset", (long) filter.page() * AUDIT_SIZE),
                (rs, ignored) -> new OpsAuditPage.Entry(rs.getLong("id"), rs.getObject("actor_id", Long.class), rs.getString("action"),
                        rs.getString("target_type"), rs.getLong("target_id"), rs.getString("reason"), rs.getTimestamp("created_at").toInstant()));
        return new OpsAuditPage(window, filter, AUDIT_SIZE, count, rows, filter.page() > 0,
                filter.page() < 500 && ((long) filter.page() + 1) * AUDIT_SIZE < count);
    }
    private static MapSqlParameterSource params(OpsReportWindow window) {
        return new MapSqlParameterSource("start", window.start().atOffset(ZoneOffset.UTC))
                .addValue("end", window.end().atOffset(ZoneOffset.UTC));
    }
}
