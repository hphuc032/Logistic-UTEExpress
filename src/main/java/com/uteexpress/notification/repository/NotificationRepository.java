package com.uteexpress.notification.repository;

import com.uteexpress.notification.dto.NotificationView;
import com.uteexpress.notification.dto.NotificationCommitted;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public NotificationRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    public List<NotificationView> inbox(Long recipientId) {
        return jdbc.getObject().query("""
                SELECT id, order_id, type, title, message, read_at, created_at
                FROM uteexpress.notifications
                WHERE recipient_id = :recipientId
                ORDER BY created_at DESC, id DESC LIMIT 100
                """, new MapSqlParameterSource("recipientId", recipientId), (rs, rowNum) ->
                new NotificationView(rs.getLong("id"), rs.getObject("order_id", Long.class),
                        rs.getString("type"), rs.getString("title"), rs.getString("message"),
                        instant(rs.getTimestamp("read_at")), instant(rs.getTimestamp("created_at"))));
    }

    public boolean belongsTo(Long id, Long recipientId) {
        Integer count = jdbc.getObject().queryForObject("""
                SELECT count(*) FROM uteexpress.notifications
                WHERE id = :id AND recipient_id = :recipientId
                """, new MapSqlParameterSource("id", id).addValue("recipientId", recipientId), Integer.class);
        return count != null && count > 0;
    }

    public void markRead(Long id, Long recipientId, Instant at) {
        jdbc.getObject().update("""
                UPDATE uteexpress.notifications SET read_at = :at
                WHERE id = :id AND recipient_id = :recipientId AND read_at IS NULL
                """, new MapSqlParameterSource("id", id).addValue("recipientId", recipientId)
                .addValue("at", Timestamp.from(at)));
    }

    public List<NotificationCommitted> insert(Long recipientId, Long orderId, String type, String title,
            String message, String dedupKey, Instant at) {
        return jdbc.getObject().query("""
                INSERT INTO uteexpress.notifications
                    (recipient_id, order_id, type, title, message, dedup_key, created_at)
                VALUES (:recipientId, :orderId, :type, :title, :message, :dedupKey, :at)
                ON CONFLICT (dedup_key) DO NOTHING RETURNING id, recipient_id
                """, new MapSqlParameterSource("recipientId", recipientId).addValue("orderId", orderId)
                .addValue("type", type).addValue("title", title).addValue("message", message)
                .addValue("dedupKey", dedupKey).addValue("at", Timestamp.from(at)),
                (rs, ignored) -> new NotificationCommitted(rs.getLong("id"), rs.getLong("recipient_id")));
    }

    // Durable source: committed lifecycle history, never a client payload or an ID high-water mark.
    private static final String HISTORY_RECIPIENTS = """
            SELECT h.id AS history_id, h.order_id, h.to_status, h.created_at, r.recipient_id,
                concat('order-history:', h.id, ':', r.recipient_id) AS dedup_key
            FROM uteexpress.order_status_history h JOIN uteexpress.orders o ON o.id=h.order_id
            LEFT JOIN uteexpress.shops s ON s.id=o.shop_id
            CROSS JOIN LATERAL (
                SELECT DISTINCT recipient_id FROM (VALUES (o.buyer_id), (s.owner_id)) ids(recipient_id)
                WHERE recipient_id IS NOT NULL
            ) r WHERE h.from_status IS NOT NULL
            """;

    public List<Long> pendingOrders() {
        return jdbc.getObject().query("""
                WITH source AS (
                """ + HISTORY_RECIPIENTS + """
                ) SELECT DISTINCT source.order_id FROM source
                WHERE NOT EXISTS (SELECT 1 FROM uteexpress.notifications n WHERE n.dedup_key=source.dedup_key)
                ORDER BY source.order_id LIMIT 100
                """, new MapSqlParameterSource(), (rs, ignored) -> rs.getLong(1));
    }

    /** One Order per transaction: never retain recipient FK locks while waiting for another Order. */
    public boolean lockProjectionOrder(Long orderId) {
        return !jdbc.getObject().query("SELECT id FROM uteexpress.orders WHERE id=:id FOR KEY SHARE",
                new MapSqlParameterSource("id", orderId), (rs, ignored) -> rs.getLong(1)).isEmpty();
    }

    public List<NotificationCommitted> projectOrderHistory(Long orderId) {
        return jdbc.getObject().query("""
                WITH source AS (
                """ + HISTORY_RECIPIENTS + """
                    AND h.order_id=:orderId
                ) INSERT INTO uteexpress.notifications
                    (recipient_id,order_id,type,title,message,dedup_key,created_at)
                SELECT recipient_id,order_id,'ORDER_STATUS','Cập nhật đơn hàng',
                    concat('Đơn hàng #',order_id,' chuyển sang ',to_status,'.'),dedup_key,created_at
                FROM source WHERE NOT EXISTS (
                    SELECT 1 FROM uteexpress.notifications n WHERE n.dedup_key=source.dedup_key)
                ORDER BY history_id,recipient_id LIMIT 200
                ON CONFLICT (dedup_key) DO NOTHING RETURNING id, recipient_id
                """, new MapSqlParameterSource("orderId", orderId),
                (rs, ignored) -> new NotificationCommitted(rs.getLong("id"), rs.getLong("recipient_id")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
