package com.uteexpress.governance.repository;

import com.uteexpress.governance.dto.OpsOrderDetail;
import com.uteexpress.governance.dto.OpsOrderSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OpsOrderQueryRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public OpsOrderQueryRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) { this.jdbc = jdbc; }

    public long count(String code, String status) {
        return jdbc.getObject().queryForObject("""
                SELECT count(*) FROM uteexpress.orders
                WHERE (CAST(:code AS varchar) IS NULL OR order_code ILIKE :code ESCAPE '\\')
                  AND (CAST(:status AS varchar) IS NULL OR status = :status)
                """, filters(code, status), Long.class);
    }

    public List<OpsOrderSummary> search(String code, String status, int limit, long offset) {
        var args = filters(code, status).addValue("limit", limit).addValue("offset", offset);
        return jdbc.getObject().query("""
                SELECT id, order_code, buyer_id, shop_id, status, grand_total, created_at, version
                FROM uteexpress.orders
                WHERE (CAST(:code AS varchar) IS NULL OR order_code ILIKE :code ESCAPE '\\')
                  AND (CAST(:status AS varchar) IS NULL OR status = :status)
                ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset
                """, args, (rs, rowNum) -> new OpsOrderSummary(rs.getLong("id"),
                rs.getString("order_code"), rs.getLong("buyer_id"), rs.getLong("shop_id"),
                rs.getString("status"), rs.getBigDecimal("grand_total"),
                instant(rs.getTimestamp("created_at")), rs.getLong("version")));
    }

    public OpsOrderDetail detail(Long id) {
        var rows = jdbc.getObject().query("""
                SELECT id, order_code, buyer_id, shop_id, status, created_at, updated_at,
                    delivered_at, cancelled_at, ready_at, version, receiver_name, phone,
                    province_code, district, detail, subtotal, discount_total, shipping_fee, grand_total
                FROM uteexpress.orders WHERE id = :id
                """, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            var facts = new OpsOrderDetail.Facts(rs.getLong("id"), rs.getString("order_code"),
                    rs.getLong("shop_id"), rs.getString("status"),
                    instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
                    instant(rs.getTimestamp("delivered_at")), instant(rs.getTimestamp("cancelled_at")),
                    new OpsOrderDetail.Address(rs.getString("receiver_name"), rs.getString("phone"),
                            rs.getString("province_code"), rs.getString("district"), rs.getString("detail")),
                    rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"),
                    rs.getBigDecimal("shipping_fee"), rs.getBigDecimal("grand_total"),
                    items(id), timeline(id), payments(id));
            return new OpsOrderDetail(rs.getLong("buyer_id"), rs.getLong("version"),
                    instant(rs.getTimestamp("ready_at")), facts);
        });
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<OpsOrderDetail.Item> items(Long orderId) {
        return jdbc.getObject().query("""
                SELECT product_id, product_name_snapshot, unit_price, discount_snapshot,
                    final_unit_price, quantity, line_total
                FROM uteexpress.order_items WHERE order_id = :orderId ORDER BY id
                """, new MapSqlParameterSource("orderId", orderId), (rs, rowNum) ->
                new OpsOrderDetail.Item(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getInt(6), rs.getBigDecimal(7)));
    }

    private List<OpsOrderDetail.TimelineEntry> timeline(Long orderId) {
        return jdbc.getObject().query("""
                SELECT from_status, to_status, created_at FROM uteexpress.order_status_history
                WHERE order_id = :orderId ORDER BY created_at, id
                """, new MapSqlParameterSource("orderId", orderId), (rs, rowNum) ->
                new OpsOrderDetail.TimelineEntry(rs.getString(1), rs.getString(2),
                        instant(rs.getTimestamp(3))));
    }

    private List<OpsOrderDetail.Payment> payments(Long orderId) {
        return jdbc.getObject().query("""
                SELECT method, status, amount, created_at FROM uteexpress.payments
                WHERE order_id = :orderId ORDER BY created_at, id
                """, new MapSqlParameterSource("orderId", orderId), (rs, rowNum) ->
                new OpsOrderDetail.Payment(rs.getString(1), rs.getString(2), rs.getBigDecimal(3),
                        instant(rs.getTimestamp(4))));
    }

    private static MapSqlParameterSource filters(String code, String status) {
        return new MapSqlParameterSource("code", code == null ? null : "%" + escape(code) + "%")
                .addValue("status", status);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
