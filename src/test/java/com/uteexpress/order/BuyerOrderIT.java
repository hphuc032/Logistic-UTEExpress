package com.uteexpress.order;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.BuyerOrderDetail;
import com.uteexpress.order.dto.BuyerOrderSummary;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.entity.OrderItem;
import com.uteexpress.order.entity.OrderStatusHistory;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.repository.OrderStatusHistoryRepository;
import com.uteexpress.order.service.BuyerOrderService;
import com.uteexpress.order.service.OrderPlacementService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.jwt.JwtTokenService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc
@Testcontainers
class BuyerOrderIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    static final Instant CREATED = Instant.parse("2026-10-03T04:05:06Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired BuyerOrderService buyerOrders;
    @Autowired OrderPlacementService placement;
    @Autowired CartService carts;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired EntityManagerFactory emf;
    @Autowired JwtTokenService tokens;
    long buyer, other;
    Order owned, foreign;

    @BeforeEach void fixture() {
        for (String table : List.of("order_status_history", "payments", "order_items", "orders")) {
            jdbc.update("DELETE FROM uteexpress." + table);
        }
        buyer = createUser();
        other = createUser();
        owned = createOrder(buyer, "OWN-ORDER", CREATED);
        foreign = createOrder(other, "FOREIGN-ORDER", CREATED.plusSeconds(1));
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void listContainsOnlyOwnSafeSummariesAndIgnoresClientIdentity() throws Exception {
        mvc.perform(get("/orders").with(user(principal(buyer, "USER")))
                        .param("buyerId", Long.toString(other)).param("userId", Long.toString(other))
                        .param("sort", "buyerId,asc").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(owned.getId()))
                .andExpect(jsonPath("$.content[0].orderCode").value("OWN-ORDER"))
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.content[0].grandTotal").value(207000))
                .andExpect(jsonPath("$.content[0].shopId").value(901))
                .andExpect(jsonPath("$.content[0].createdAt").value(CREATED.toString()))
                .andExpect(jsonPath("$.content[0].buyerId").doesNotExist())
                .andExpect(jsonPath("$.content[0].address").doesNotExist())
                .andExpect(jsonPath("$.content[0].commissionAmount").doesNotExist())
                .andExpect(jsonPath("$.content[0].requestHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].checkoutKey").doesNotExist())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(content().string(not(containsString("FOREIGN-ORDER"))));
    }

    @Test void pagesUseNewestTimestampThenIdAndCountsAreBuyerScoped() throws Exception {
        Order tie = createOrder(buyer, "TIE", CREATED);
        Order newest = createOrder(buyer, "NEWEST", CREATED.plusSeconds(20));
        Order oldest = createOrder(buyer, "OLDEST-HIGH-ID", CREATED.minusSeconds(20));
        authenticate(buyer, "USER");
        var first = buyerOrders.list(0, 2);
        var second = buyerOrders.list(1, 2);
        assertThat(first.content()).extracting(BuyerOrderSummary::id).containsExactly(newest.getId(), tie.getId());
        assertThat(second.content()).extracting(BuyerOrderSummary::id).containsExactly(owned.getId(), oldest.getId());
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.hasPrevious()).isFalse();
        assertThat(second.hasNext()).isFalse();
        assertThat(second.hasPrevious()).isTrue();
        assertThat(buyerOrders.list(0, 2)).isEqualTo(first);
        mvc.perform(get("/orders?page=1&size=2").with(user(principal(buyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(view().name("order/list"))
                .andExpect(content().string(containsString("/orders?page=0&amp;size=2")))
                .andExpect(content().string(not(containsString("NEWEST"))))
                .andExpect(content().string(not(containsString("FOREIGN-ORDER"))));
    }

    @Test void databaseLimitsEntityLoadsAndListQueryCountDoesNotGrowWithPageSize() {
        for (int index = 0; index < 6; index++) createOrder(buyer, "MORE-" + index, CREATED);
        authenticate(buyer, "USER");
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        for (int size : List.of(2, 5)) {
            statistics.clear();
            assertThat(buyerOrders.list(0, size).content()).hasSize(size);
            assertThat(statistics.getEntityLoadCount()).isEqualTo(size);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2); // content + scoped count, no child reads
        }
    }

    @Test void emptyBuyerAndOutOfRangePageRemainEmpty() throws Exception {
        long emptyBuyer = createUser();
        mvc.perform(get("/orders").with(user(principal(emptyBuyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/orders").with(user(principal(emptyBuyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa có đơn hàng")));
        mvc.perform(get("/orders?page=99&size=2").with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.hasNext").value(false));
    }

    @ParameterizedTest
    @CsvSource({"page,-1,VALIDATION_FAILED", "size,0,VALIDATION_FAILED", "size,-1,VALIDATION_FAILED",
            "size,101,VALIDATION_FAILED", "page,abc,INVALID_REQUEST", "size,1.5,INVALID_REQUEST",
            "page,2147483648,INVALID_REQUEST", "size,999999999999999999999,INVALID_REQUEST",
            "page,2147483647,VALIDATION_FAILED"})
    void invalidPaginationReturnsControlled400ForBothRepresentations(String name, String value, ErrorCode code) throws Exception {
        for (MediaType type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            var response = mvc.perform(get("/orders").param(name, value).with(user(principal(buyer, "USER"))).accept(type))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(type));
            if (type.equals(MediaType.APPLICATION_JSON)) {
                response.andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value(code.name()))
                        .andExpect(jsonPath("$.message").value(code.message()));
            } else {
                response.andExpect(view().name("order/error"))
                        .andExpect(model().attribute("errorCode", code.name()))
                        .andExpect(model().attribute("publicMessage", code.message()))
                        .andExpect(content().string(containsString("Không thể xem đơn hàng")))
                        .andExpect(content().string(not(containsString("OWN-ORDER"))));
            }
        }
    }

    @Test void largestSupportedJpaOffsetAndSizeLimitAreSafe() throws Exception {
        mvc.perform(get("/orders?page=2147483647&size=1").with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/orders?size=100").with(user(principal(buyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());
    }

    @Test void detailMapsSnapshotsMoneyAndOnlyPersistedTimeline() throws Exception {
        mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owned.getId()))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.address.receiverName").value("Receiver <script>"))
                .andExpect(jsonPath("$.address.phone").value("0900000000"))
                .andExpect(jsonPath("$.address.provinceCode").value("VN"))
                .andExpect(jsonPath("$.address.district").value("District"))
                .andExpect(jsonPath("$.address.detail").value("Snapshot address"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(902))
                .andExpect(jsonPath("$.items[0].productNameSnapshot").value("Snapshot <script>"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100000))
                .andExpect(jsonPath("$.items[0].discountSnapshot").value(20000))
                .andExpect(jsonPath("$.items[0].finalUnitPrice").value(80000))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(160000))
                .andExpect(jsonPath("$.items[1].lineTotal").value(40000))
                .andExpect(jsonPath("$.subtotal").value(200000)).andExpect(jsonPath("$.discountTotal").value(10000))
                .andExpect(jsonPath("$.shippingFee").value(17000)).andExpect(jsonPath("$.grandTotal").value(207000))
                .andExpect(jsonPath("$.timeline.length()").value(1))
                .andExpect(jsonPath("$.timeline[0].fromStatus").isEmpty())
                .andExpect(jsonPath("$.timeline[0].toStatus").value("NEW"))
                .andExpect(jsonPath("$.timeline[0].createdAt").value(CREATED.toString()))
                .andExpect(jsonPath("$.payments").isEmpty())
                .andExpect(jsonPath("$.buyerId").doesNotExist()).andExpect(jsonPath("$.commissionAmount").doesNotExist())
                .andExpect(jsonPath("$.commissionRateSnapshot").doesNotExist())
                .andExpect(jsonPath("$.commissionPolicyId").doesNotExist())
                .andExpect(jsonPath("$.checkoutKey").doesNotExist()).andExpect(jsonPath("$.requestHash").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist()).andExpect(jsonPath("$.timeline[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.timeline[0].reason").doesNotExist());
    }

    @Test void htmlEscapesSnapshotsFormatsUtcAndVndAndShowsNoInventedPayment() throws Exception {
        var result = mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(view().name("order/detail"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("Snapshot &lt;script&gt;")))
                .andExpect(content().string(containsString("Receiver &lt;script&gt;")))
                .andExpect(content().string(not(containsString("Snapshot <script>"))))
                .andExpect(content().string(containsString("03/10/2026 04:05:06")))
                .andExpect(content().string(containsString("207.000 ₫")))
                .andExpect(content().string(containsString("Chưa có thông tin phương thức hoặc trạng thái thanh toán")))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("UNPAID", "COD", "CONFIRMED", "INTERNAL-REASON");
    }

    @ParameterizedTest @ValueSource(strings = {"application/json", "text/html"})
    void foreignAndMissingOrdersHaveSameSafeResponse(String type) throws Exception {
        for (long id : List.of(foreign.getId(), Long.MAX_VALUE, 0L, -1L)) {
            var response = mvc.perform(get("/orders/{id}", id).with(user(principal(buyer, "USER"))).accept(type)
                            .param("buyerId", Long.toString(other)).param("userId", Long.toString(other)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(type))
                    .andExpect(content().string(not(containsString("FOREIGN-ORDER"))))
                    .andExpect(content().string(not(containsString("Snapshot address"))));
            if (type.equals("application/json")) {
                response.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                        .andExpect(jsonPath("$.message").value("Resource was not found."));
            } else {
                response.andExpect(view().name("order/error"))
                        .andExpect(model().attribute("errorCode", "RESOURCE_NOT_FOUND"))
                        .andExpect(model().attribute("publicMessage", "Resource was not found."))
                        .andExpect(content().string(containsString("RESOURCE_NOT_FOUND")))
                        .andExpect(content().string(containsString("Resource was not found.")));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "9223372036854775808"})
    void malformedOrderIdsAreControlled400(String id) throws Exception {
        for (MediaType type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            var response = mvc.perform(get("/orders/{id}", id).with(user(principal(buyer, "USER"))).accept(type))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(type));
            if (type.equals(MediaType.APPLICATION_JSON)) {
                response.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            } else {
                response.andExpect(view().name("order/error"))
                        .andExpect(model().attribute("errorCode", "INVALID_REQUEST"));
            }
        }
    }

    @Test void timelineRetainsRecordedTransitionsAndTimestampTiesWithoutInventingMissingEdges() {
        // Persisted fixture facts deliberately arrive out of chronological insertion order.
        history.saveAndFlush(new OrderStatusHistory(owned.getId(), OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED, null, CREATED.plusSeconds(20), "INTERNAL-REASON"));
        history.saveAndFlush(new OrderStatusHistory(owned.getId(), OrderStatus.NEW,
                OrderStatus.CONFIRMED, buyer, CREATED, "INTERNAL-REASON"));
        jdbc.update("UPDATE uteexpress.orders SET status='CANCELLED',cancelled_at=?,updated_at=? WHERE id=?",
                Timestamp.from(CREATED.plusSeconds(20)), Timestamp.from(CREATED.plusSeconds(30)), owned.getId());
        authenticate(buyer, "USER");
        var detail = buyerOrders.detail(owned.getId());
        assertThat(detail.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(detail.timeline()).extracting(BuyerOrderDetail.TimelineEntry::toStatus)
                .containsExactly(OrderStatus.NEW, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(detail.timeline()).extracting(BuyerOrderDetail.TimelineEntry::createdAt)
                .containsExactly(CREATED, CREATED, CREATED.plusSeconds(20));
        assertThat(detail.timeline().get(2).fromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(detail.cancelledAt()).isEqualTo(CREATED.plusSeconds(20));
        assertThat(detail.updatedAt()).isEqualTo(CREATED.plusSeconds(30));
        assertThat(detail.deliveredAt()).isNull();
    }

    @Test void returnRejectionIsShownAsRecordedAndDoesNotResetDeliveryTimestamp() throws Exception {
        Instant delivered = CREATED.plusSeconds(10);
        history.saveAndFlush(new OrderStatusHistory(owned.getId(), OrderStatus.SHIPPING,
                OrderStatus.DELIVERED, buyer, delivered, null));
        history.saveAndFlush(new OrderStatusHistory(owned.getId(), OrderStatus.DELIVERED,
                OrderStatus.RETURN_REQUESTED, buyer, CREATED.plusSeconds(20), null));
        history.saveAndFlush(new OrderStatusHistory(owned.getId(), OrderStatus.RETURN_REQUESTED,
                OrderStatus.DELIVERED, other, CREATED.plusSeconds(30), "Internal rejection"));
        jdbc.update("UPDATE uteexpress.orders SET status='DELIVERED',delivered_at=?,updated_at=? WHERE id=?",
                Timestamp.from(delivered), Timestamp.from(CREATED.plusSeconds(30)), owned.getId());
        mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeline.length()").value(4))
                .andExpect(jsonPath("$.timeline[3].fromStatus").value("RETURN_REQUESTED"))
                .andExpect(jsonPath("$.timeline[3].toStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").value(delivered.toString()))
                .andExpect(content().string(not(containsString("Internal rejection"))));
    }

    @Test void missingHistoryStaysEmptyEvenWhenCurrentStatusAndTimestampsExist() throws Exception {
        jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=?", owned.getId());
        jdbc.update("UPDATE uteexpress.orders SET status='CONFIRMED',updated_at=? WHERE id=?",
                Timestamp.from(CREATED.plusSeconds(10)), owned.getId());
        mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.timeline").isEmpty());
        mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa có lịch sử trạng thái được ghi nhận.")));
    }

    @Test void paymentRecordsExposePersistedMethodAndStatusWithoutAttemptSecretsOrAggregation() throws Exception {
        payment(owned.getId(), "COD", "UNPAID");
        payment(owned.getId(), "ONLINE", "PAID");
        payment(foreign.getId(), "ONLINE", "UNPAID");
        mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].method").value("COD"))
                .andExpect(jsonPath("$.payments[0].status").value("UNPAID"))
                .andExpect(jsonPath("$.payments[1].method").value("ONLINE"))
                .andExpect(jsonPath("$.payments[1].status").value("PAID"))
                .andExpect(jsonPath("$.payments[1].amount").value(207000))
                .andExpect(jsonPath("$.payments[0].attemptKey").doesNotExist())
                .andExpect(jsonPath("$.payments[0].providerReference").doesNotExist())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist())
                .andExpect(content().string(not(containsString("private-payment"))));
        mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("COD")))
                .andExpect(content().string(containsString("UNPAID"))).andExpect(content().string(containsString("ONLINE")))
                .andExpect(content().string(not(containsString("private-payment"))));
    }

    @ParameterizedTest @ValueSource(strings = {"application/json", "text/html"})
    void unauthenticatedRequestsUseExisting401Behavior(String type) throws Exception {
        for (String path : List.of("/orders", "/orders/" + owned.getId())) {
            mvc.perform(get(path).accept(type)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER", "SHIPPER"})
    void nonBuyerRolesAreDeniedEvenForTheirOwnOrder(String role) throws Exception {
        for (MediaType type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            for (String path : List.of("/orders", "/orders/" + owned.getId())) {
                var response = mvc.perform(get(path).with(user(principal(buyer, role))).accept(type))
                        .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(type))
                        .andExpect(content().string(not(containsString("OWN-ORDER"))));
                if (type.equals(MediaType.APPLICATION_JSON)) {
                    response.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
                } else {
                    response.andExpect(view().name("order/error"))
                            .andExpect(model().attribute("errorCode", "ACCESS_DENIED"));
                }
            }
        }
    }

    @Test void vendorUsesExistingBuyerPolicyAndStillCannotAccessForeignOrder() throws Exception {
        mvc.perform(get("/orders").with(user(principal(buyer, "VENDOR"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());
        mvc.perform(get("/orders/{id}", foreign.getId()).with(user(principal(buyer, "VENDOR"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test void usernameThatLooksLikeBuyerIdIsNeverParsedAsIdentity() throws Exception {
        mvc.perform(get("/orders").with(user(Long.toString(buyer)).roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test void jwtCookieResolvesPersistedBuyerAndRechecksActiveAccount() throws Exception {
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='USER'", buyer);
        var cookie = new Cookie("UTEEXPRESS_AUTH", tokens.issue(principal(buyer, "USER")));
        mvc.perform(get("/orders").cookie(cookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(owned.getId()));
        mvc.perform(get("/orders/{id}", foreign.getId()).cookie(cookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", buyer);
        mvc.perform(get("/orders/{id}", owned.getId()).cookie(cookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test void getRequestsDoNotMutateOrderPaymentHistoryOrInventory() throws Exception {
        var before = databaseState();
        for (MediaType type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            mvc.perform(get("/orders").with(user(principal(buyer, "USER"))).accept(type)).andExpect(status().isOk());
            mvc.perform(get("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).accept(type)).andExpect(status().isOk());
        }
        mvc.perform(post("/orders/{id}", owned.getId()).with(user(principal(buyer, "USER"))).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test void actualCheckoutOrderIsImmediatelyVisibleWithItsInitialHistoryAndUnpaidCodPayment() throws Exception {
        long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id",
                Long.class, buyer, UUID.randomUUID().toString());
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id",
                Long.class, UUID.randomUUID().toString());
        long product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Placed snapshot',125000,10,'ACTIVE') RETURNING id",
                Long.class, shop, category);
        long address = jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','Placed address',true) RETURNING id",
                Long.class, buyer);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id",
                Long.class, "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',17000,true)", provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7,?,?)",
                Timestamp.from(Instant.now().minusSeconds(60)), buyer);
        authenticate(buyer, "USER");
        carts.addProduct(new AddCartProductRequest(product, 2));
        var receipt = placement.placeOrder(new CheckoutRequest(UUID.randomUUID().toString(),
                List.of(new CheckoutRequest.Item(product, 2)), address, provider, "STANDARD", CheckoutRequest.PaymentMethod.COD, null));
        clearAuthentication();
        mvc.perform(get("/orders/{id}", receipt.orderId()).with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderCode").value(receipt.orderCode()))
                .andExpect(jsonPath("$.grandTotal").value(267000))
                .andExpect(jsonPath("$.items[0].productNameSnapshot").value("Placed snapshot"))
                .andExpect(jsonPath("$.address.detail").value("Placed address"))
                .andExpect(jsonPath("$.timeline.length()").value(1))
                .andExpect(jsonPath("$.timeline[0].toStatus").value("NEW"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].method").value("COD"))
                .andExpect(jsonPath("$.payments[0].status").value("UNPAID"))
                .andExpect(jsonPath("$.payments[0].amount").value(267000));
    }

    private Order createOrder(long owner, String code, Instant at) {
        // Trusted persisted fixtures exercise reads; they do not enable any lifecycle write endpoint.
        var quote = new CheckoutQuote(901L, List.of(
                new CheckoutQuote.ItemSnapshot(902L, "Snapshot <script>", money("100000"), money("20000"), money("80000"), 2, money("160000")),
                new CheckoutQuote.ItemSnapshot(903L, "Second snapshot", money("40000"), money("0"), money("40000"), 1, money("40000"))),
                new CheckoutQuote.AddressSnapshot("Receiver <script>", "0900000000", "VN", "District", "Snapshot address"),
                OrderTotals.calculate(money("200000"), money("10000"), money("17000")),
                904L, "STANDARD", null, money("10"), money("19000"));
        Order order = orders.saveAndFlush(new Order(owner, code, UUID.randomUUID().toString(), "private-request-hash", quote, at));
        items.saveAllAndFlush(quote.items().stream().map(item -> new OrderItem(order.getId(), item)).toList());
        history.saveAndFlush(new OrderStatusHistory(order.getId(), null, OrderStatus.NEW, owner, at, "INTERNAL-REASON"));
        return order;
    }

    private long createUser() {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, name + "@example.test", name + "@example.test", name, name);
    }

    private void payment(long orderId, String method, String status) {
        String key = "private-payment-" + UUID.randomUUID();
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,provider_reference,created_at) VALUES (?,?,?,207000,?,?,?)",
                orderId, method, status, key, key, Timestamp.from(CREATED));
    }

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }

    private static UteExpressPrincipal principal(long id, String role) {
        return new UteExpressPrincipal(id, "opaque-buyer-" + id, null, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
    }

    private static void authenticate(long id, String role) {
        var principal = principal(id, role);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private Map<String, List<Map<String, Object>>> databaseState() {
        Map<String, List<Map<String, Object>>> state = new TreeMap<>();
        for (String table : List.of("orders", "order_items", "order_status_history", "payments", "products", "carts", "cart_items")) {
            state.put(table, jdbc.queryForList("SELECT * FROM uteexpress." + table + " ORDER BY id"));
        }
        return state;
    }
}
