package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.dto.CartProductSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/** Catalog-owned read model over product availability; exposes no foreign entity. */
@Repository
public class CatalogReadRepository {
    private static final String PURCHASABLE = "p.status = 'ACTIVE' AND s.status = 'APPROVED' AND c.active = TRUE";
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
                   AND (%s)
                 ORDER BY p.id
                """.formatted(PURCHASABLE), new MapSqlParameterSource("productIds", productIds),
                (row, ignored) -> new ProductSnapshot(
                        row.getLong("id"),
                        row.getLong("shop_id"),
                        row.getString("name"),
                        row.getBigDecimal("price"),
                        row.getLong("version")));
    }

    public List<CartProductSnapshot> findCartProducts(Set<Long> productIds) {
        return jdbc.getObject().query("""
                SELECT p.id, p.name, p.price, p.stock, COALESCE((%s), FALSE) AS purchasable
                  FROM uteexpress.products p
                  LEFT JOIN uteexpress.shops s ON s.id = p.shop_id
                  LEFT JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id IN (:productIds)
                 ORDER BY p.id
                """.formatted(PURCHASABLE), new MapSqlParameterSource("productIds", productIds),
                (row, ignored) -> new CartProductSnapshot(row.getLong("id"),
                        row.getString("name"), row.getBigDecimal("price"), row.getInt("stock"),
                        row.getBoolean("purchasable")));
    }
}
