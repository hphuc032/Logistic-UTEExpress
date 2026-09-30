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
    static final String MODERATED_NAME = "Bưu kiện mẫu kiểm duyệt";
    static final String SECOND_SHOP_SLUG = "demo-catalog-shop-2";
    private static final String SECOND_OWNER_EMAIL = "demo-catalog-vendor2@example.invalid";
    private static final String SECOND_OWNER_USERNAME = "demo_catalog_vendor2";

    private static final List<DemoProduct> PRIMARY_PRODUCTS = List.of(
            product(ACTIVE_NAME, "Giải pháp đóng gói tiêu chuẩn cho đơn giao nội thành", "125000", 12),
            product(OUT_OF_STOCK_NAME, "Sản phẩm mẫu kiểm thử trạng thái hết hàng", "85000", 0),
            product("Gói chuyển phát tiết kiệm", "Vật tư đóng gói gọn nhẹ cho đơn hàng thường", "45000", 30),
            product("Bộ đóng gói chống sốc", "Bộ vật liệu bảo vệ hàng dễ vỡ khi vận chuyển", "159000", 18),
            product("Hộp lưu trữ hồ sơ", "Hộp giấy cứng dành cho tài liệu và hồ sơ", "72000", 24),
            product("Túi giao hàng tái sử dụng", "Túi bền chắc phù hợp nhiều hành trình giao nhận", "99000", 16));
    private static final List<DemoProduct> SECONDARY_PRODUCTS = List.of(
            product("Tem vận chuyển chống nước", "Bộ tem bám chắc, phù hợp kiện hàng đường dài", "39000", 50),
            product("Cuộn màng bọc bảo vệ", "Màng bọc trong giúp cố định kiện hàng", "119000", 22),
            product("Thùng carton cỡ nhỏ", "Thùng carton cho phụ kiện và sản phẩm nhỏ", "28000", 40),
            product("Thùng carton cỡ vừa", "Thùng carton nhiều lớp cho đơn hàng phổ thông", "46000", 35),
            product("Băng keo niêm phong", "Băng keo bền, dễ nhận biết khi kiện bị mở", "34000", 60),
            product("Nhãn hàng dễ vỡ", "Nhãn cảnh báo rõ ràng cho kiện cần bảo quản", "25000", 45));

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
        Long secondShopId = ensureSecondApprovedShop();

        PRIMARY_PRODUCTS.forEach(product -> seed(shopId, categoryId, product, "ACTIVE", null));
        SECONDARY_PRODUCTS.forEach(product -> seed(secondShopId, categoryId, product, "ACTIVE", null));
        seed(shopId, categoryId, product(HIDDEN_NAME, "Sản phẩm mẫu do Vendor ẩn", "99000", 5),
                "HIDDEN", null);
        seed(shopId, categoryId, product(MODERATED_NAME, "Sản phẩm mẫu bị giới hạn công khai", "109000", 5),
                "MODERATED", "Demo moderation fixture");
    }

    private Long requiredId(String sql, String slug, String label, String property) {
        List<Long> ids = jdbc.queryForList(sql, Long.class, slug);
        if (ids.size() != 1) {
            throw new IllegalStateException("Catalog demo seed requires one " + label
                    + " selected by " + property + ".");
        }
        return ids.getFirst();
    }

    private Long ensureSecondApprovedShop() {
        jdbc.update("""
                INSERT INTO uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash, status)
                VALUES (?, ?, ?, ?, 'DEMO_ACCOUNT_DISABLED', 'DISABLED')
                ON CONFLICT DO NOTHING
                """, SECOND_OWNER_EMAIL, SECOND_OWNER_EMAIL, SECOND_OWNER_USERNAME, SECOND_OWNER_USERNAME);
        List<Long> ownerIds = jdbc.queryForList("""
                SELECT id FROM uteexpress.users
                 WHERE normalized_email = ? AND normalized_username = ? AND status = 'DISABLED'
                """, Long.class, SECOND_OWNER_EMAIL, SECOND_OWNER_USERNAME);
        if (ownerIds.size() != 1) {
            throw new IllegalStateException("Catalog demo seed requires its reserved disabled demo owner.");
        }
        Long ownerId = ownerIds.getFirst();
        jdbc.update("""
                INSERT INTO uteexpress.shops
                    (owner_id, name, slug, description, pickup_address, status)
                SELECT ?, 'UTEExpress Demo Logistics', ?,
                       'Cửa hàng mẫu thứ hai dành riêng cho hồ sơ demo.',
                       'Demo-only pickup address', 'APPROVED'
                 WHERE NOT EXISTS (SELECT 1 FROM uteexpress.shops WHERE slug = ?)
                """, ownerId, SECOND_SHOP_SLUG, SECOND_SHOP_SLUG);
        List<Long> shopIds = jdbc.queryForList("""
                SELECT id FROM uteexpress.shops
                 WHERE slug = ? AND owner_id = ? AND status = 'APPROVED'
                """, Long.class, SECOND_SHOP_SLUG, ownerId);
        if (shopIds.size() != 1) {
            throw new IllegalStateException("Catalog demo seed requires its reserved second approved Shop.");
        }
        return shopIds.getFirst();
    }

    private void seed(Long shopId, Long categoryId, DemoProduct product, String status, String moderationReason) {
        jdbc.update("""
                INSERT INTO uteexpress.products
                    (shop_id, category_id, name, description, price, stock, status, moderation_reason)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?
                 WHERE NOT EXISTS (
                    SELECT 1 FROM uteexpress.products WHERE shop_id = ? AND name = ?
                 )
                """, shopId, categoryId, product.name(), product.description(), product.price(), product.stock(),
                status, moderationReason, shopId, product.name());
    }

    private static DemoProduct product(String name, String description, String price, int stock) {
        return new DemoProduct(name, description, new BigDecimal(price), stock);
    }

    private record DemoProduct(String name, String description, BigDecimal price, int stock) {
    }
}
