package com.uteexpress.engagement.repository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class EngagementRepository {
    public record SavedRow(Long productId, Instant savedAt) { }

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public EngagementRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) { this.jdbc = jdbc; }

    public void addFavorite(Long userId, Long productId, Instant now) {
        jdbc.getObject().update("""
                INSERT INTO uteexpress.favorites (user_id, product_id, created_at)
                VALUES (:userId, :productId, :now)
                ON CONFLICT (user_id, product_id) DO NOTHING
                """, params(userId, productId).addValue("now", Timestamp.from(now)));
    }

    public void removeFavorite(Long userId, Long productId) {
        jdbc.getObject().update("""
                DELETE FROM uteexpress.favorites WHERE user_id = :userId AND product_id = :productId
                """, params(userId, productId));
    }

    public boolean isFavorite(Long userId, Long productId) {
        Integer count = jdbc.getObject().queryForObject("""
                SELECT count(*) FROM uteexpress.favorites WHERE user_id = :userId AND product_id = :productId
                """, params(userId, productId), Integer.class);
        return count != null && count > 0;
    }

    public List<SavedRow> favorites(Long userId) {
        return jdbc.getObject().query("""
                SELECT product_id, created_at FROM uteexpress.favorites
                WHERE user_id = :userId ORDER BY created_at DESC, product_id DESC
                """, new MapSqlParameterSource("userId", userId),
                (row, ignored) -> new SavedRow(row.getLong(1), row.getTimestamp(2).toInstant()));
    }

    public void recordView(Long userId, Long productId, Instant now) {
        // Serialize trimming for this account, including simultaneous first-time views.
        jdbc.getObject().queryForObject("""
                SELECT id FROM uteexpress.users WHERE id = :userId FOR UPDATE
                """, new MapSqlParameterSource("userId", userId), Long.class);
        Timestamp latest = jdbc.getObject().queryForObject("""
                SELECT max(viewed_at) FROM uteexpress.product_views WHERE user_id = :userId
                """, new MapSqlParameterSource("userId", userId), Timestamp.class);
        Instant viewedAt = latest == null ? now : now.isAfter(latest.toInstant())
                ? now : latest.toInstant().plusNanos(1000);
        jdbc.getObject().update("""
                INSERT INTO uteexpress.product_views (user_id, product_id, viewed_at)
                VALUES (:userId, :productId, :now)
                ON CONFLICT (user_id, product_id) DO UPDATE SET viewed_at = EXCLUDED.viewed_at
                """, params(userId, productId).addValue("now", Timestamp.from(viewedAt)));
        jdbc.getObject().update("""
                DELETE FROM uteexpress.product_views
                 WHERE user_id = :userId AND product_id IN (
                       SELECT product_id FROM uteexpress.product_views WHERE user_id = :userId
                       ORDER BY viewed_at DESC, product_id DESC OFFSET 30)
                """, new MapSqlParameterSource("userId", userId));
    }

    public List<SavedRow> recent(Long userId) {
        return jdbc.getObject().query("""
                SELECT product_id, viewed_at FROM uteexpress.product_views
                WHERE user_id = :userId ORDER BY viewed_at DESC, product_id DESC LIMIT 30
                """, new MapSqlParameterSource("userId", userId),
                (row, ignored) -> new SavedRow(row.getLong(1), row.getTimestamp(2).toInstant()));
    }

    private static MapSqlParameterSource params(Long userId, Long productId) {
        return new MapSqlParameterSource("userId", userId).addValue("productId", productId);
    }
}
