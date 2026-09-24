package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.dto.ProductSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/** Catalog-owned read model over product availability; exposes no foreign entity. */
@Repository
public class CatalogReadRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public CatalogReadRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProductSnapshot> findPurchasableByIds(Set<Long> productIds) {
        return jdbc.getObject().query("""
                SELECT p.id, p.shop_id, p.name, p.price, p.version
                  FROM uteexpress.products p
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id IN (:productIds)
                   AND p.status = 'ACTIVE'
                   AND s.status = 'APPROVED'
                   AND c.active = TRUE
                 ORDER BY p.id
                """, new MapSqlParameterSource("productIds", productIds),
                (row, ignored) -> new ProductSnapshot(
                        row.getLong("id"),
                        row.getLong("shop_id"),
                        row.getString("name"),
                        row.getBigDecimal("price"),
                        row.getLong("version")));
    }
}
