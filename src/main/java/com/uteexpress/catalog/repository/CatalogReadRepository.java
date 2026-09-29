package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.dto.CartProductSnapshot;
import com.uteexpress.catalog.dto.ProductCard;
import com.uteexpress.catalog.dto.ProductDetailView;
import com.uteexpress.catalog.dto.ProductImageView;
import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.dto.PublicCategorySummary;
import com.uteexpress.catalog.dto.PublicShopSummary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Catalog-owned read model over product availability; exposes no foreign entity. */
@Repository
public class CatalogReadRepository {
    /** One authoritative predicate shared by commerce and every public read. */
    private static final String PUBLIC_VISIBLE =
            "p.status = 'ACTIVE' AND s.status = 'APPROVED' AND c.active = TRUE";
    private static final String PUBLIC_CARD_QUERY = """
            SELECT p.id, p.name, p.price, p.stock,
                   s.slug AS shop_slug, s.name AS shop_name,
                   c.slug AS category_slug, c.name AS category_name,
                   thumbnail.id AS thumbnail_image_id
              FROM uteexpress.products p
              JOIN uteexpress.shops s ON s.id = p.shop_id
              JOIN uteexpress.categories c ON c.id = p.category_id
              LEFT JOIN LATERAL (
                    SELECT pi.id
                      FROM uteexpress.product_images pi
                     WHERE pi.product_id = p.id
                     ORDER BY pi.position, pi.id
                     LIMIT 1
              ) thumbnail ON TRUE
             WHERE (%s)
            """.formatted(PUBLIC_VISIBLE);
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
                """.formatted(PUBLIC_VISIBLE), new MapSqlParameterSource("productIds", productIds),
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
                """.formatted(PUBLIC_VISIBLE), new MapSqlParameterSource("productIds", productIds),
                (row, ignored) -> new CartProductSnapshot(row.getLong("id"),
                        row.getString("name"), row.getBigDecimal("price"), row.getInt("stock"),
                        row.getBoolean("purchasable")));
    }

    public List<ProductCard> findFeaturedPublicProducts(int limit) {
        return queryCards(PUBLIC_CARD_QUERY + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
                new MapSqlParameterSource("limit", limit));
    }

    public List<ProductCard> findAllPublicProducts() {
        return queryCards(PUBLIC_CARD_QUERY + " ORDER BY p.created_at DESC, p.id DESC",
                new MapSqlParameterSource());
    }

    public List<ProductCard> findPublicProductsByCategorySlug(String slug) {
        return queryCards(PUBLIC_CARD_QUERY + " AND c.slug = :slug ORDER BY p.created_at DESC, p.id DESC",
                new MapSqlParameterSource("slug", slug));
    }

    public List<ProductCard> findPublicProductsByShopSlug(String slug) {
        return queryCards(PUBLIC_CARD_QUERY + " AND s.slug = :slug ORDER BY p.created_at DESC, p.id DESC",
                new MapSqlParameterSource("slug", slug));
    }

    public Optional<ProductDetailView> findPublicProduct(Long productId) {
        return jdbc.getObject().query("""
                SELECT p.id, p.name, p.description, p.price, p.stock,
                       s.slug AS shop_slug, s.name AS shop_name,
                       c.slug AS category_slug, c.name AS category_name
                  FROM uteexpress.products p
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id = :productId
                   AND (%s)
                """.formatted(PUBLIC_VISIBLE), new MapSqlParameterSource("productId", productId),
                (row, ignored) -> new ProductDetailView(
                        row.getLong("id"), row.getString("name"), row.getString("description"),
                        row.getBigDecimal("price"), row.getInt("stock"), row.getString("shop_slug"),
                        row.getString("shop_name"), row.getString("category_slug"),
                        row.getString("category_name"), List.of())).stream().findFirst();
    }

    public List<ProductImageView> findPublicProductImages(Long productId) {
        return jdbc.getObject().query("""
                SELECT pi.id, pi.position, pi.alt_text
                  FROM uteexpress.product_images pi
                  JOIN uteexpress.products p ON p.id = pi.product_id
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id = :productId
                   AND (%s)
                 ORDER BY pi.position, pi.id
                """.formatted(PUBLIC_VISIBLE), new MapSqlParameterSource("productId", productId),
                (row, ignored) -> new ProductImageView(
                        row.getLong("id"), row.getInt("position"), row.getString("alt_text")));
    }

    public Optional<String> findPublicImageStorageKey(Long productId, Long imageId) {
        return jdbc.getObject().query("""
                SELECT pi.storage_key
                  FROM uteexpress.product_images pi
                  JOIN uteexpress.products p ON p.id = pi.product_id
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id = :productId
                   AND pi.id = :imageId
                   AND pi.product_id = p.id
                   AND (%s)
                """.formatted(PUBLIC_VISIBLE), new MapSqlParameterSource()
                        .addValue("productId", productId).addValue("imageId", imageId),
                (row, ignored) -> row.getString("storage_key")).stream().findFirst();
    }

    public List<PublicCategorySummary> findPublicCategories() {
        return jdbc.getObject().query("""
                SELECT c.slug, c.name
                  FROM uteexpress.categories c
                 WHERE c.active = TRUE
                 ORDER BY c.name, c.slug
                """, (row, ignored) -> new PublicCategorySummary(
                        row.getString("slug"), row.getString("name")));
    }

    public Optional<PublicCategorySummary> findPublicCategory(String slug) {
        return jdbc.getObject().query("""
                SELECT c.slug, c.name
                  FROM uteexpress.categories c
                 WHERE c.slug = :slug AND c.active = TRUE
                """, new MapSqlParameterSource("slug", slug),
                (row, ignored) -> new PublicCategorySummary(
                        row.getString("slug"), row.getString("name"))).stream().findFirst();
    }

    public List<PublicShopSummary> findPublicShops() {
        return jdbc.getObject().query("""
                SELECT s.slug, s.name, s.description
                  FROM uteexpress.shops s
                 WHERE s.status = 'APPROVED'
                 ORDER BY s.name, s.slug
                """, (row, ignored) -> new PublicShopSummary(
                        row.getString("slug"), row.getString("name"), row.getString("description")));
    }

    public Optional<PublicShopSummary> findPublicShop(String slug) {
        return jdbc.getObject().query("""
                SELECT s.slug, s.name, s.description
                  FROM uteexpress.shops s
                 WHERE s.slug = :slug AND s.status = 'APPROVED'
                """, new MapSqlParameterSource("slug", slug),
                (row, ignored) -> new PublicShopSummary(
                        row.getString("slug"), row.getString("name"), row.getString("description")))
                .stream().findFirst();
    }

    private List<ProductCard> queryCards(String query, MapSqlParameterSource parameters) {
        return jdbc.getObject().query(query, parameters, (row, ignored) -> new ProductCard(
                row.getLong("id"), row.getString("name"), row.getBigDecimal("price"), row.getInt("stock"),
                row.getString("shop_slug"), row.getString("shop_name"), row.getString("category_slug"),
                row.getString("category_name"), row.getObject("thumbnail_image_id", Long.class)));
    }
}
