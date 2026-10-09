package com.uteexpress.notification.repository;

import com.uteexpress.notification.dto.NotificationView;
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

    public void insert(Long recipientId, Long orderId, String type, String title,
            String message, String dedupKey, Instant at) {
        jdbc.getObject().update("""
                INSERT INTO uteexpress.notifications
                    (recipient_id, order_id, type, title, message, dedup_key, created_at)
                VALUES (:recipientId, :orderId, :type, :title, :message, :dedupKey, :at)
                ON CONFLICT (dedup_key) DO NOTHING
                """, new MapSqlParameterSource("recipientId", recipientId).addValue("orderId", orderId)
                .addValue("type", type).addValue("title", title).addValue("message", message)
                .addValue("dedupKey", dedupKey).addValue("at", Timestamp.from(at)));
    }

    public List<Long> orderRecipients(Long orderId) {
        return jdbc.getObject().query("""
                SELECT DISTINCT recipient_id FROM (
                    SELECT o.buyer_id AS recipient_id FROM uteexpress.orders o WHERE o.id = :orderId
                    UNION ALL
                    SELECT s.owner_id AS recipient_id FROM uteexpress.orders o
                    JOIN uteexpress.shops s ON s.id = o.shop_id WHERE o.id = :orderId
                ) recipients
                """, new MapSqlParameterSource("orderId", orderId), (rs, rowNum) -> rs.getLong(1));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
