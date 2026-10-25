package com.uteexpress.integration;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** One persisted Order traverses existing owner modules; no synthetic lifecycle SQL or mock principals. */
@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class Int02CommerceFlowIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    private static final String PASSWORD = "Integration02!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @ParameterizedTest(name = "checkout through full COD refund; first-attempt failure={0}")
    @ValueSource(booleans = {false, true})
    void realJwtCheckoutDeliveryReviewReturnAndRefundPreserveOneOrdersEvidence(boolean retry) throws Exception {
        Account buyer = account("USER"), vendor = account("VENDOR"), admin = account("ADMIN");
        Account shipper = account("SHIPPER"), foreignShipper = account("SHIPPER"), stranger = account("USER");
        Cookie buyerJwt = login(buyer), vendorJwt = login(vendor), adminJwt = login(admin);
        Cookie shipperJwt = login(shipper), foreignJwt = login(foreignShipper), strangerJwt = login(stranger);
        Fixture f = fixture(buyer.id(), vendor.id(), admin.id());
        mvc.perform(get("/products/{id}", f.product())).andExpect(status().isOk());
        json("/user/cart/items", buyerJwt, "{\"productId\":%d,\"quantity\":2}".formatted(f.product()))
                .andExpect(status().isOk());
        json("/user/cart/items", buyerJwt, "{\"productId\":%d,\"quantity\":1}".formatted(f.otherProduct()))
                .andExpect(status().isOk());
        long unselected = jdbc.queryForObject("SELECT ci.id FROM uteexpress.cart_items ci JOIN uteexpress.carts c ON c.id=ci.cart_id WHERE c.user_id=? AND ci.product_id=?",
                Long.class, buyer.id(), f.otherProduct());
        json("/user/cart/items/" + unselected + "/selection", buyerJwt, "{\"selected\":false}")
                .andExpect(status().isOk());
        String selection = "\"addressId\":%d,\"shippingProviderId\":%d,\"shippingServiceCode\":\"STANDARD\",\"voucherCode\":\"%s\""
                .formatted(f.address(), f.provider(), f.voucher());
        json("/user/checkout/quote", buyerJwt, "{" + selection + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.totals.subtotal").value(180000))
                .andExpect(jsonPath("$.quote.totals.discountTotal").value(10000))
                .andExpect(jsonPath("$.quote.totals.grandTotal").value(185000));
        String checkout = "{\"checkoutKey\":\"%s\",\"items\":[{\"productId\":%d,\"quantity\":2}],%s,\"paymentMethod\":\"COD\"}"
                .formatted(UUID.randomUUID(), f.product(), selection);
        json("/user/checkout/place-order", buyerJwt, checkout).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW")).andExpect(jsonPath("$.replayed").value(false));
        long order = jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE buyer_id=?", Long.class, buyer.id());
        var orderSnapshot = immutableOrder(order);
        var itemsSnapshot = rows("order_items", order);
        assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, f.product())).isEqualTo(8);
        money(orderSnapshot, "commission_amount", "11900");
        money(itemsSnapshot.getFirst(), "unit_price", "100000");
        money(itemsSnapshot.getFirst(), "discount_snapshot", "10000");
        money(itemsSnapshot.getFirst(), "line_total", "180000");
        assertThat(itemsSnapshot).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.cart_items WHERE id=? AND NOT selected", Integer.class, unselected)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.cart_items ci JOIN uteexpress.carts c ON c.id=ci.cart_id WHERE c.user_id=?", Integer.class, buyer.id())).isOne();
        var created = state(order);
        json("/user/checkout/place-order", buyerJwt, checkout).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order)).andExpect(jsonPath("$.replayed").value(true));
        assertThat(state(order)).isEqualTo(created);
        mvc.perform(get("/orders/{id}", order).cookie(strangerJwt).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        json("/vendor/orders/" + order + "/confirm", vendorJwt, "{\"expectedVersion\":0}").andExpect(status().isOk());
        json("/vendor/orders/" + order + "/ready", vendorJwt, "{\"expectedVersion\":%d}".formatted(version(order))).andExpect(status().isOk());
        json("/admin/orders/" + order + "/assign", adminJwt,
                "{\"shipperId\":%d,\"expectedOrderVersion\":%d}".formatted(shipper.id(), version(order)))
                .andExpect(status().isCreated());
        var assigned = state(order);
        json("/shipper/orders/" + order + "/pickup", foreignJwt, fulfillment(order, null)).andExpect(status().isNotFound());
        assertThat(state(order)).isEqualTo(assigned);
        json("/shipper/orders/" + order + "/pickup", shipperJwt, fulfillment(order, null)).andExpect(status().isOk());
        json("/shipper/orders/" + order + "/shipping", shipperJwt, fulfillment(order, null)).andExpect(status().isOk());
        if (retry) {
            long originalOrderVersion = version(order);
            json("/shipper/orders/" + order + "/delivery-failed", shipperJwt,
                    fulfillment(order, null).replace("}", ",\"failureReason\":\"Recipient unavailable\",\"evidenceReference\":\"INT02-TEST-ATTEMPT\"}"))
                    .andExpect(status().isOk());
            assertThat(version(order)).isEqualTo(originalOrderVersion);
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order)).isEqualTo("SHIPPING");
            json("/shipper/orders/" + order + "/retry", shipperJwt, fulfillment(order, null)).andExpect(status().isOk());
            assertThat(version(order)).isEqualTo(originalOrderVersion);
        }
        var beforeWrongCod = state(order);
        json("/shipper/orders/" + order + "/delivered", shipperJwt, fulfillment(order, "1"))
                .andExpect(status().isConflict());
        assertThat(state(order)).isEqualTo(beforeWrongCod);
        json("/shipper/orders/" + order + "/delivered", shipperJwt, fulfillment(order, "185000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        var paid = rows("payments", order);
        assertThat(paid).hasSize(1);
        assertThat(paid.getFirst()).containsEntry("status", "PAID");
        money(paid.getFirst(), "amount", "185000");
        long item = ((Number) itemsSnapshot.getFirst().get("id")).longValue();
        String comment = "<script>unsafeReview()</script> Sản phẩm đã giao đúng, nội dung đánh giá đủ dài để kiểm thử tích hợp.";
        String review = "{\"rating\":5,\"comment\":\"" + comment + "\"}";
        json("/order-items/" + item + "/review", strangerJwt, review).andExpect(status().isNotFound());
        json("/order-items/" + item + "/review", buyerJwt, review).andExpect(status().isOk());
        json("/order-items/" + item + "/review", buyerJwt, review).andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("SELECT user_id,product_id,order_item_id,order_id FROM uteexpress.reviews WHERE order_item_id=?", item))
                .containsEntry("user_id", buyer.id()).containsEntry("product_id", f.product())
                .containsEntry("order_item_id", item).containsEntry("order_id", order);
        mvc.perform(get("/products/{id}/reviews", f.product())).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;unsafeReview()")))
                .andExpect(content().string(not(containsString("<script>unsafeReview()"))));

        json("/orders/" + order + "/return", buyerJwt,
                "{\"expectedVersion\":%d,\"reason\":\"Return request for integration test\"}".formatted(version(order)))
                .andExpect(status().isOk());
        json("/vendor/orders/" + order + "/return/approve", vendorJwt, returnVersions(order)).andExpect(status().isOk());
        json("/vendor/orders/" + order + "/return/receive", vendorJwt,
                returnVersions(order).replace("}", ",\"receiptReference\":\"INT02-TEST-RECEIPT\"}"))
                .andExpect(status().isOk());
        var receipt = rows("return_requests", order);
        var deliveredShipment = rows("shipments", order);
        String refund = "{\"expectedOrderVersion\":%d,\"expectedReturnRequestVersion\":%d,\"proofReference\":\"INT02-TEST-PROOF\"}"
                .formatted(version(order), requestVersion(order));
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/admin/orders/{id}/refunds", order).cookie(adminJwt).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(refund))
                .andExpect(status().isForbidden());
        refund(order, buyerJwt, key, refund).andExpect(status().isForbidden());
        refund(order, adminJwt, key, refund).andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount").value(185000)).andExpect(jsonPath("$.replay").value(false));
        var refunded = state(order);
        refund(order, adminJwt, key, refund).andExpect(status().isOk()).andExpect(jsonPath("$.replay").value(true));
        refund(order, adminJwt, key, refund.replace("INT02-TEST-PROOF", "CHANGED-TEST-PROOF"))
                .andExpect(status().isConflict());
        // PostgreSQL BYTEA values are returned as fresh byte[] instances on each read.
        assertThat(state(order)).usingRecursiveComparison().isEqualTo(refunded);
        var ledger = rows("refunds", order);
        assertThat(ledger).hasSize(1);
        assertThat(ledger.getFirst()).containsEntry("status", "SUCCEEDED")
                .containsEntry("payment_id", paid.getFirst().get("id"))
                .containsEntry("return_request_id", receipt.getFirst().get("id"));
        assertThat(rows("return_requests", order)).isEqualTo(receipt);
        assertThat(rows("shipments", order)).isEqualTo(deliveredShipment);
        assertThat(rows("payments", order)).isEqualTo(paid);
        assertThat(immutableOrder(order)).isEqualTo(orderSnapshot);
        assertThat(rows("order_items", order)).isEqualTo(itemsSnapshot);
        assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, f.product())).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.voucher_usages WHERE order_id=?", String.class, order)).isEqualTo("REDEEMED");
        assertThat(jdbc.queryForList("SELECT to_status FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", String.class, order))
                .containsExactly("NEW", "CONFIRMED", "PICKED_UP", "SHIPPING", "DELIVERED", "RETURN_REQUESTED", "RETURNED", "REFUNDED");
        assertThat(jdbc.queryForList("SELECT DISTINCT recipient_id FROM uteexpress.notifications WHERE order_id=?", Long.class, order))
                .containsExactlyInAnyOrder(buyer.id(), vendor.id());
        assertThat(rows("notifications", order)).hasSize(14);
        mvc.perform(get("/notifications/inbox").cookie(strangerJwt)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/notifications/inbox").cookie(buyerJwt)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/orders/{id}", order).cookie(buyerJwt).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("REFUNDED")));
        // Already-written Reviews stay PUBLIC after a subsequent Return/Refund under current HP policy.
        mvc.perform(get("/products/{id}/reviews", f.product())).andExpect(status().isOk())
                .andExpect(content().string(containsString("unsafeReview()")));
    }

    private ResultActions json(String path, Cookie jwt, String body) throws Exception {
        return mvc.perform(post(path).cookie(jwt).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).content(body));
    }
    private ResultActions refund(long order, Cookie jwt, String key, String body) throws Exception {
        return mvc.perform(post("/admin/orders/{id}/refunds", order).cookie(jwt).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body));
    }
    private long version(long order) { return jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?", Long.class, order); }
    private long requestVersion(long order) { return jdbc.queryForObject("SELECT version FROM uteexpress.return_requests WHERE order_id=?", Long.class, order); }
    private String returnVersions(long order) { return "{\"expectedVersion\":%d,\"expectedRequestVersion\":%d}".formatted(version(order), requestVersion(order)); }
    private String fulfillment(long order, String amount) {
        long sv = jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?", Long.class, order);
        return "{\"expectedOrderVersion\":%d,\"expectedShipmentVersion\":%d%s}".formatted(version(order), sv,
                amount == null ? "" : ",\"collectedAmount\":" + amount);
    }
    private List<Map<String, Object>> rows(String table, long order) {
        // Fixed test-owned table names only, never request input.
        return jdbc.queryForList("SELECT * FROM uteexpress." + table + " WHERE order_id=? ORDER BY id", order);
    }
    private Map<String, Object> state(long order) {
        return Map.of("order", jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order),
                "shipment", rows("shipments", order), "payment", rows("payments", order),
                "history", rows("order_status_history", order), "notifications", rows("notifications", order),
                "refund", rows("refunds", order));
    }
    private Map<String, Object> immutableOrder(long order) {
        return jdbc.queryForMap("""
                SELECT buyer_id,shop_id,receiver_name,phone,province_code,district,detail,subtotal,discount_total,
                    shipping_fee,grand_total,commission_policy_id,commission_rate_snapshot,commission_amount,
                    shipping_provider_id,shipping_service_code,voucher_id,voucher_code,voucher_scope
                FROM uteexpress.orders WHERE id=?
                """, order);
    }
    private static void money(Map<String, Object> row, String column, String expected) {
        assertThat((BigDecimal) row.get(column)).as(column).isEqualByComparingTo(expected);
    }
    private Cookie login(Account account) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("identifier", account.username()).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }
    private Account account(String role) {
        String name = "int02_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long id = jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name, passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role);
        return new Account(id, name);
    }
    private Fixture fixture(long buyer, long vendor, long admin) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('INT02',?,true) RETURNING id", Long.class, "int02-" + suffix);
        long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'INT02',?,'Pickup','APPROVED') RETURNING id", Long.class, vendor, "int02-" + suffix);
        long product = product(shop, category), other = product(shop, category);
        long address = jdbc.queryForObject("""
                INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default)
                VALUES (?,'Integration Buyer','0900000000','VN','Test district','Test address',true) RETURNING id
                """, Long.class, buyer);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'INT02',true) RETURNING id", Long.class, "I02" + suffix.toUpperCase(java.util.Locale.ROOT));
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',15000,true)", provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7,CURRENT_TIMESTAMP - INTERVAL '1 day',?)", admin);
        jdbc.update("""
                INSERT INTO uteexpress.promotions(product_id,name,discount_percent,starts_at,ends_at,active,created_by)
                VALUES (?,'INT02',10,CURRENT_TIMESTAMP - INTERVAL '1 day',CURRENT_TIMESTAMP + INTERVAL '1 day',true,?)
                """, product, vendor);
        String voucher = "I02" + suffix.toUpperCase(java.util.Locale.ROOT);
        jdbc.update("""
                INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by)
                VALUES (?,'SHOP','FIXED',?,10000,0,CURRENT_TIMESTAMP - INTERVAL '1 day',CURRENT_TIMESTAMP + INTERVAL '1 day',10,1,?)
                """, voucher, shop, vendor);
        return new Fixture(product, other, address, provider, voucher);
    }
    private long product(long shop, long category) {
        return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'INT02 product',100000,10,'ACTIVE') RETURNING id", Long.class, shop, category);
    }
    private record Account(long id, String username) { }
    private record Fixture(long product, long otherProduct, long address, long provider, String voucher) { }
}
