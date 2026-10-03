package com.uteexpress.governance.repository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class CommissionPolicyRepository {
    public record PolicyRow(Long id, BigDecimal ratePercent, Instant effectiveFrom,
                            boolean active, Long createdBy) { }

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public CommissionPolicyRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    public List<PolicyRow> latestAt(Instant at) {
        return jdbc.getObject().query("""
                SELECT id, rate_percent, effective_from, active, created_by
                FROM uteexpress.commission_policies
                WHERE effective_from <= :at
                ORDER BY effective_from DESC LIMIT 2
                """, new MapSqlParameterSource("at", Timestamp.from(at)), (row, ignored) ->
                new PolicyRow(row.getLong(1), row.getBigDecimal(2), row.getTimestamp(3).toInstant(),
                        row.getBoolean(4), row.getLong(5)));
    }

    public Long create(BigDecimal ratePercent, Instant effectiveFrom, Long actorId) {
        return jdbc.getObject().queryForObject("""
                INSERT INTO uteexpress.commission_policies (rate_percent, effective_from, created_by)
                VALUES (:rate, :effectiveFrom, :actorId) RETURNING id
                """, new MapSqlParameterSource("rate", ratePercent)
                .addValue("effectiveFrom", Timestamp.from(effectiveFrom))
                .addValue("actorId", actorId), Long.class);
    }

    public List<PolicyRow> list() {
        return jdbc.getObject().query("""
                SELECT id, rate_percent, effective_from, active, created_by
                FROM uteexpress.commission_policies ORDER BY effective_from DESC LIMIT 100
                """, new MapSqlParameterSource(), (row, ignored) ->
                new PolicyRow(row.getLong(1), row.getBigDecimal(2), row.getTimestamp(3).toInstant(),
                        row.getBoolean(4), row.getLong(5)));
    }
}
