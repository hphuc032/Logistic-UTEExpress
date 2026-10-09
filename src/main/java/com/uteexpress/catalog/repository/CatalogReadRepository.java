package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.dto.CartProductSnapshot;
import com.uteexpress.catalog.dto.ProductCard;
import com.uteexpress.catalog.dto.ProductDetailView;
import com.uteexpress.catalog.dto.ProductImageView;
import com.uteexpress.catalog.dto.ProductSearchCriteria;
import com.uteexpress.catalog.dto.ProductSort;
import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.dto.PublicCategorySummary;
import com.uteexpress.catalog.dto.PublicShopSummary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Catalog-owned read model over product availability; exposes no foreign entity. */
@Repository
public class CatalogReadRepository {
    public record CheckoutAvailabilityKey(Long productId, Long shopId, Long categoryId) { }
    /** One authoritative predicate shared by commerce and every public read. */
    private static final String PUBLIC_VISIBLE =
            "p.status = 'ACTIVE' AND s.status = 'APPROVED' AND c.active = TRUE";
    private static final String PUBLIC_CARD_QUERY = """
            SELECT p.id, p.name, p.price, prices.discount AS promotion_discount, prices.effective_price, p.stock,
                   s.slug AS shop_slug, s.name AS shop_name,
                   c.slug AS category_slug, c.name AS category_name,
                   thumbnail.id AS thumbnail_image_id
              FROM uteexpress.products p
              JOIN uteexpress.product_prices_at(:pricingAt) prices ON prices.product_id = p.id
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
    private static final String SALES_JOIN = """
              LEFT JOIN (
                    SELECT oi.product_id, SUM(oi.quantity) AS sold_quantity
                      FROM uteexpress.order_items oi
                      JOIN uteexpress.orders o ON o.id = oi.order_id
                     WHERE o.status = 'DELIVERED'
                     GROUP BY oi.product_id
              ) sales ON sales.product_id = p.id
            """;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final Clock clock;

    public CatalogReadRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<ProductSnapshot> findPurchasableByIds(Set<Long> productIds) {
        return jdbc.getObject().query("""
                SELECT p.id, p.shop_id, p.name, p.price, prices.discount AS promotion_discount, prices.effective_price, p.version
                  FROM uteexpress.products p
                  JOIN uteexpress.product_prices_at(:pricingAt) prices ON prices.product_id = p.id
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id IN (:productIds)
                   AND (%s)
                 ORDER BY p.id
                """.formatted(PUBLIC_VISIBLE), parameters("productIds", productIds),
                (row, ignored) -> new ProductSnapshot(
                        row.getLong("id"),
                        row.getLong("shop_id"),
                        row.getString("name"),
                        row.getBigDecimal("price"),
                        row.getLong("version"), row.getBigDecimal("promotion_discount"), row.getBigDecimal("effective_price")));
    }

    public List<CheckoutAvailabilityKey> findCheckoutAvailabilityKeys(Set<Long> productIds) {
        return jdbc.getObject().query("""
                SELECT p.id, p.shop_id, p.category_id
                  FROM uteexpress.products p
                  JOIN uteexpress.product_prices_at(:pricingAt) prices ON prices.product_id = p.id
                 WHERE p.id IN (:productIds)
                 ORDER BY p.id
                """, parameters("productIds", productIds),
                (row, ignored) -> new CheckoutAvailabilityKey(row.getLong("id"),
                        row.getLong("shop_id"), row.getLong("category_id")));
    }

    public List<CartProductSnapshot> findCartProducts(Set<Long> productIds) {
        return jdbc.getObject().query("""
                SELECT p.id, p.name, p.price, prices.discount AS promotion_discount, prices.effective_price, p.stock, COALESCE((%s), FALSE) AS purchasable
                  FROM uteexpress.products p
                  JOIN uteexpress.product_prices_at(:pricingAt) prices ON prices.product_id = p.id
                  LEFT JOIN uteexpress.shops s ON s.id = p.shop_id
                  LEFT JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id IN (:productIds)
                 ORDER BY p.id
                """.formatted(PUBLIC_VISIBLE), parameters("productIds", productIds),
                (row, ignored) -> new CartProductSnapshot(row.getLong("id"),
                        row.getString("name"), row.getBigDecimal("price"), row.getInt("stock"),
                        row.getBoolean("purchasable"), row.getBigDecimal("promotion_discount"), row.getBigDecimal("effective_price")));
    }

    public List<ProductCard> findFeaturedPublicProducts(int limit) {
        return queryCards(PUBLIC_CARD_QUERY + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
                parameters("limit", limit));
    }

    public List<ProductCard> findAllPublicProducts() {
        return queryCards(PUBLIC_CARD_QUERY + " ORDER BY p.created_at DESC, p.id DESC",
                parameters());
    }

    public long countPublicProducts(ProductSearchCriteria criteria) {
        return countPublicProducts(criteria, clock.instant());
    }

    public long countPublicProducts(ProductSearchCriteria criteria, Instant pricingAt) {
        StringBuilder query = new StringBuilder("""
                SELECT COUNT(*)
                  FROM uteexpress.products p
                  JOIN uteexpress.product_prices_at(:pricingAt) prices ON prices.product_id = p.id
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE (%s)
                """.formatted(PUBLIC_VISIBLE));
        MapSqlParameterSource parameters = parameters().addValue("pricingAt", Timestamp.from(pricingAt));
        appendDiscoveryFilters(query, parameters, criteria);
        Long count = jdbc.getObject().queryForObject(query.toString(), parameters, Long.class);
        return count == null ? 0 : count;
    }

    public List<ProductCard> findPublicProducts(ProductSearchCriteria criteria, ProductSort sort,
            int limit, long offset) {
        return findPublicProducts(criteria, sort, limit, offset, clock.instant());
    }

    public List<ProductCard> findPublicProducts(ProductSearchCriteria criteria, ProductSort sort,
            int limit, long offset, Instant pricingAt) {
        StringBuilder query = new StringBuilder("""
                SELECT p.id, p.name, p.price, prices.discount AS promotion_discount, prices.effective_price, p.stock,
                       s.slug AS shop_slug, s.name AS shop_name,
                       c.slug AS category_slug, c.name AS category_name,
                       thumbnail.id AS thumbnail_image_id
                  FROM uteexpress.products p
                  JOIN uteexpress.product_prices_at(:pricingAt) prices ON prices.product_id = p.id
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                  LEFT JOIN LATERAL (
                        SELECT pi.id
                          FROM uteexpress.product_images pi
                         WHERE pi.product_id = p.id
                         ORDER BY pi.position, pi.id
                         LIMIT 1
                  ) thumbnail ON TRUE
                """);
        if (sort == ProductSort.BEST_SELLING) {
            query.append(SALES_JOIN);
        }
        query.append(" WHERE (").append(PUBLIC_VISIBLE).append(')');
        MapSqlParameterSource parameters = parameters().addValue("pricingAt", Timestamp.from(pricingAt));
        appendDiscoveryFilters(query, parameters, criteria);
        query.append(orderBy(sort)).append(" LIMIT :limit OFFSET :offset");
        parameters.addValue("limit", limit).addValue("offset", offset);
        return queryCards(query.toString(), parameters);
    }

    public List<ProductCard> findPublicProductsByCategorySlug(String slug) {
        return queryCards(PUBLIC_CARD_QUERY + " AND c.slug = :slug ORDER BY p.created_at DESC, p.id DESC",
                parameters("slug", slug));
    }

    public List<ProductCard> findPublicProductsByShopSlug(String slug) {
        return queryCards(PUBLIC_CARD_QUERY + " AND s.slug = :slug ORDER BY p.created_at DESC, p.id DESC",
                parameters("slug", slug));
    }

    public Optional<ProductDetailView> findPublicProduct(Long productId) {
        return jdbc.getObject().query("""
                SELECT p.id, p.name, p.description, p.price, prices.discount AS promotion_discount, prices.effective_price, p.stock,
                       s.slug AS shop_slug, s.name AS shop_name,
                       c.slug AS category_slug, c.name AS category_name
                  FROM uteexpress.products p
                  JOIN uteexpress.product_prices_at(:pricingAt) prices ON prices.product_id = p.id
                  JOIN uteexpress.shops s ON s.id = p.shop_id
                  JOIN uteexpress.categories c ON c.id = p.category_id
                 WHERE p.id = :productId
                   AND (%s)
                """.formatted(PUBLIC_VISIBLE), parameters("productId", productId),
                (row, ignored) -> new ProductDetailView(
                        row.getLong("id"), row.getString("name"), row.getString("description"),
                        row.getBigDecimal("price"), row.getInt("stock"), row.getString("shop_slug"),
                        row.getString("shop_name"), row.getString("category_slug"),
                        row.getString("category_name"), List.of(), row.getBigDecimal("promotion_discount"), row.getBigDecimal("effective_price"))).stream().findFirst();
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
                """.formatted(PUBLIC_VISIBLE), parameters("productId", productId),
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
                """.formatted(PUBLIC_VISIBLE), parameters()
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
                """, parameters("slug", slug),
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
                """, parameters("slug", slug),
                (row, ignored) -> new PublicShopSummary(
                        row.getString("slug"), row.getString("name"), row.getString("description")))
                .stream().findFirst();
    }

    private List<ProductCard> queryCards(String query, MapSqlParameterSource parameters) {
        return jdbc.getObject().query(query, parameters, (row, ignored) -> new ProductCard(
                row.getLong("id"), row.getString("name"), row.getBigDecimal("price"), row.getInt("stock"),
                row.getString("shop_slug"), row.getString("shop_name"), row.getString("category_slug"),
                row.getString("category_name"), row.getObject("thumbnail_image_id", Long.class), row.getBigDecimal("promotion_discount"), row.getBigDecimal("effective_price")));
    }

    private MapSqlParameterSource parameters() {
        return new MapSqlParameterSource("pricingAt", Timestamp.from(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }
    private MapSqlParameterSource parameters(String name, Object value) {
        return parameters().addValue(name, value);
    }

    private static void appendDiscoveryFilters(StringBuilder query, MapSqlParameterSource parameters,
            ProductSearchCriteria criteria) {
        if (criteria.getQ() != null) {
            query.append(" AND p.name ILIKE :namePattern ESCAPE '\\'");
            parameters.addValue("namePattern", "%" + escapeLike(criteria.getQ()) + "%");
        }
        if (criteria.getShop() != null) {
            query.append(" AND s.slug = :shopSlug");
            parameters.addValue("shopSlug", criteria.getShop());
        }
        if (criteria.getCategory() != null) {
            query.append(" AND c.slug = :categorySlug");
            parameters.addValue("categorySlug", criteria.getCategory());
        }
        if (criteria.getMinPrice() != null) {
            query.append(" AND prices.effective_price >= :minPrice");
            parameters.addValue("minPrice", criteria.getMinPrice());
        }
        if (criteria.getMaxPrice() != null) {
            query.append(" AND prices.effective_price <= :maxPrice");
            parameters.addValue("maxPrice", criteria.getMaxPrice());
        }
    }

    private static String orderBy(ProductSort sort) {
        return switch (sort) {
            case PRICE_ASC -> " ORDER BY prices.effective_price ASC, p.id ASC";
            case PRICE_DESC -> " ORDER BY prices.effective_price DESC, p.id DESC";
            case BEST_SELLING -> " ORDER BY COALESCE(sales.sold_quantity, 0) DESC,"
                    + " p.created_at DESC, p.id DESC";
            case NEWEST -> " ORDER BY p.created_at DESC, p.id DESC";
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
