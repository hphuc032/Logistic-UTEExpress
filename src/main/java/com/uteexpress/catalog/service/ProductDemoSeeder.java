package com.uteexpress.catalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Explicit demo-only fixtures. Existing rows are never updated or reset. */
@Component
@Profile("demo & !prod")
@ConditionalOnProperty(name = "uteexpress.demo.catalog.enabled", havingValue = "true")
public class ProductDemoSeeder implements ApplicationRunner {
    static final String ACTIVE_NAME = "Bưu kiện mẫu tiêu chuẩn";
    static final String OUT_OF_STOCK_NAME = "Bưu kiện mẫu hết hàng";
    static final String HIDDEN_NAME = "Bưu kiện mẫu ẩn";

    private final JdbcTemplate jdbc;
    private final String shopSlug;
    private final String categorySlug;

    public ProductDemoSeeder(
            JdbcTemplate jdbc,
            @Value("${uteexpress.demo.catalog.shop-slug:demo-shop}") String shopSlug,
            @Value("${uteexpress.demo.catalog.category-slug:demo-category}") String categorySlug) {
        this.jdbc = jdbc;
        this.shopSlug = shopSlug;
        this.categorySlug = categorySlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtext('uteexpress:prod00-demo-products'))");
        Long shopId = requiredId(
                "SELECT id FROM uteexpress.shops WHERE slug = ? AND status = 'APPROVED'",
                shopSlug, "approved Shop", "uteexpress.demo.catalog.shop-slug");
        Long categoryId = requiredId(
                "SELECT id FROM uteexpress.categories WHERE slug = ? AND active = TRUE",
                categorySlug, "active Category", "uteexpress.demo.catalog.category-slug");

        seed(shopId, categoryId, ACTIVE_NAME, "Sản phẩm mẫu đang bán", "125000", 12, "ACTIVE");
        seed(shopId, categoryId, OUT_OF_STOCK_NAME, "Sản phẩm mẫu kiểm thử hết hàng", "85000", 0, "ACTIVE");
        seed(shopId, categoryId, HIDDEN_NAME, "Sản phẩm mẫu không công khai", "99000", 5, "HIDDEN");
    }

    private Long requiredId(String sql, String slug, String label, String property) {
        List<Long> ids = jdbc.queryForList(sql, Long.class, slug);
        if (ids.size() != 1) {
            throw new IllegalStateException("Catalog demo seed requires one " + label
                    + " selected by " + property + ".");
        }
        return ids.getFirst();
    }

    private void seed(Long shopId, Long categoryId, String name, String description,
            String price, int stock, String status) {
        jdbc.update("""
                INSERT INTO uteexpress.products
                    (shop_id, category_id, name, description, price, stock, status)
                SELECT ?, ?, ?, ?, ?, ?, ?
                 WHERE NOT EXISTS (
                    SELECT 1 FROM uteexpress.products WHERE shop_id = ? AND name = ?
                 )
                """, shopId, categoryId, name, description, new BigDecimal(price), stock, status,
                shopId, name);
    }
}
