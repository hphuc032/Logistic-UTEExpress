package com.uteexpress.payment;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.service.OrderPlacementService;
import com.uteexpress.payment.dto.VnpayIpnResponse;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.payment.service.VnpayGateway;
import com.uteexpress.payment.service.VnpayPaymentService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "uteexpress.payment.vnpay.enabled=true",
        "uteexpress.payment.vnpay.tmn-code=TESTCODE",
        "uteexpress.payment.vnpay.hash-secret=integration-test-secret",
        "uteexpress.payment.vnpay.return-url=https://uteexpress-web.onrender.com/payments/vnpay/return"})
@AutoConfigureMockMvc @Testcontainers
class VnpayPaymentIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired VnpayPaymentService payments;
    @Autowired VnpayGateway gateway;
    @Autowired PaymentReadService reads;
    @Autowired OrderRepository orders;
    @Autowired OrderPlacementService placement;
    @Autowired CartService carts;
    @Autowired PlatformTransactionManager transactionManager;
    TransactionTemplate transactions;
    long buyer, orderId;

    @BeforeEach void setup() {
        transactions = new TransactionTemplate(transactionManager);
        buyer = createUser();
        orderId = createOrder(buyer);
        transactions.executeWithoutResult(tx -> payments.initializeForNewOrder(orderId));
    }

    private long createOrder(long owner) {
        var quote = new CheckoutQuote(901L,
                List.of(new CheckoutQuote.ItemSnapshot(902L, "Snapshot", money("100000"),
                        money("0"), money("100000"), 1, money("100000"))),
                new CheckoutQuote.AddressSnapshot("Receiver", "0900000000", "VN", "District", "Address"),
                OrderTotals.calculate(money("100000"), money("10000"), money("17000")),
                903L, "STANDARD", null, money("0"), money("0"));
        return orders.saveAndFlush(new Order(owner, "PAY03-" + UUID.randomUUID(), UUID.randomUUID().toString(),
                "private-hash", quote, Instant.now())).getId();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void anonymousReturnNeverMarksPaidAndIpnAloneConfirmsExactlyOnce() throws Exception {
        var fields = callback();
        var beforeOrder = order();
        mvc.perform(callbackRequest("return", fields)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đang chờ thông báo")));
        assertUnpaid();
        mvc.perform(callbackRequest("ipn", fields)).andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00")).andExpect(jsonPath("$.Message").value("Confirm Success"));
        var paid = payment();
        assertThat(paid).containsEntry("status", "PAID").containsEntry("provider_reference", "VNPAY:" + transactionNumber());
        assertThat(paid.get("paid_at")).isNotNull();
        mvc.perform(callbackRequest("ipn", fields)).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(payment()).isEqualTo(paid);
        assertThat(order()).isEqualTo(beforeOrder);
        mvc.perform(callbackRequest("return", fields)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thanh toán thành công")));
        transactions.executeWithoutResult(tx -> reads.requireConfirmablePayment(orderId, money("107000")));
    }

    @ParameterizedTest @ValueSource(strings = {"signature", "amount", "merchant", "reference", "status", "transaction", "zeroTransaction", "date", "lateDate", "earlyDate"})
    void invalidCallbacksLeavePersistedStateUntouched(String kind) throws Exception {
        var fields = callback();
        String expected = "99";
        switch (kind) {
            case "signature" -> { fields.put("vnp_Amount", "1"); expected = "97"; }
            case "amount" -> { fields.put("vnp_Amount", "1"); expected = "04"; sign(fields); }
            case "merchant" -> { fields.put("vnp_TmnCode", "OTHERONE"); sign(fields); }
            case "reference" -> { fields.put("vnp_TxnRef", "VP" + "a".repeat(32)); expected = "01"; sign(fields); }
            case "status" -> { fields.remove("vnp_TransactionStatus"); sign(fields); }
            case "transaction" -> { fields.put("vnp_TransactionNo", "forged"); sign(fields); }
            case "zeroTransaction" -> { fields.put("vnp_TransactionNo", "0"); sign(fields); }
            case "date" -> { fields.put("vnp_PayDate", "20260230080000"); sign(fields); }
            case "lateDate" -> { fields.put("vnp_PayDate", "20990101000000"); sign(fields); }
            case "earlyDate" -> { fields.put("vnp_PayDate", "20200101000000"); sign(fields); }
            default -> throw new AssertionError(kind);
        }
        var before = payment();
        mvc.perform(callbackRequest("ipn", fields)).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value(expected));
        assertThat(payment()).isEqualTo(before);
    }

    @Test void duplicateParametersAreRejectedBeforeSignatureVerification() throws Exception {
        var before = payment();
        mvc.perform(callbackRequest("ipn", callback()).param("vnp_Amount", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));
        assertThat(payment()).isEqualTo(before);
        mvc.perform(callbackRequest("return", callback()).param("vnp_Amount", "100"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Không thể xác minh")));
    }

    @Test void validFailureClosesAttemptWithoutPaidStateOrDuplicateEffects() {
        var fields = callback();
        fields.put("vnp_ResponseCode", "24"); fields.put("vnp_TransactionStatus", "02");
        fields.put("vnp_TransactionNo", "0"); sign(fields);
        assertThat(payments.acceptIpn(fields).code()).isEqualTo("00");
        var failed = payment();
        assertUnpaid();
        assertThat(failed.get("expired_at")).isNotNull();
        assertThat(payments.acceptIpn(fields).code()).isEqualTo("02");
        assertThat(payments.acceptIpn(callback()).code()).isEqualTo("02");
        assertThat(payment()).isEqualTo(failed);
    }

    @Test void pendingNotificationDoesNotCloseOrConfirmAttempt() {
        var fields = callback(); fields.put("vnp_ResponseCode", "01"); fields.put("vnp_TransactionStatus", "01");
        fields.put("vnp_TransactionNo", "0"); sign(fields);
        var before = payment();
        assertThat(payments.acceptIpn(fields).code()).isEqualTo("00");
        assertThat(payment()).isEqualTo(before);
    }

    @Test void cancelledOrderAndAmountDriftCannotBePaid() {
        jdbc.update("UPDATE uteexpress.orders SET status='CANCELLED' WHERE id=?", orderId);
        assertThat(payments.acceptIpn(callback()).code()).isEqualTo("99");
        assertUnpaid();
        jdbc.update("UPDATE uteexpress.orders SET status='NEW',shipping_fee=18000,grand_total=108000 WHERE id=?", orderId);
        assertThat(payments.acceptIpn(callback()).code()).isEqualTo("04");
        assertUnpaid();
    }

    @Test void differentTransactionCannotOverwritePreviouslyConfirmedProviderReference() {
        assertThat(payments.acceptIpn(callback()).code()).isEqualTo("00");
        var before = payment();
        var fields = callback(); fields.put("vnp_TransactionNo", "987654321"); sign(fields);
        assertThat(payments.acceptIpn(fields).code()).isEqualTo("99");
        assertThat(payment()).isEqualTo(before);
    }

    @Test void delayedIpnCanConfirmAPaymentMadeWithinItsOriginalWindow() {
        jdbc.update("UPDATE uteexpress.payments SET created_at=CURRENT_TIMESTAMP - INTERVAL '20 minutes' WHERE order_id=?", orderId);
        var fields = callback();
        Instant created = ((java.sql.Timestamp) payment().get("created_at")).toInstant();
        fields.put("vnp_PayDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7))
                .format(created.plusSeconds(60)));
        sign(fields);
        assertThat(payments.acceptIpn(fields).code()).isEqualTo("00");
        assertThat(payment()).containsEntry("status", "PAID");
    }

    @Test void ambiguousPaymentRecordsCannotBeSelectedOrRepaired() {
        var fields = callback();
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,provider_reference,attempt_key,created_at,updated_at) "
                + "VALUES (?,'ONLINE','UNPAID',107000,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                orderId, "VNPAY:" + transactionNumber(), "historic-" + UUID.randomUUID());
        // Ambiguous/mixed historical evidence must never be selected or silently repaired.
        assertThat(payments.acceptIpn(fields).code()).isEqualTo("01");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.payments WHERE order_id=? AND status='PAID'", Integer.class, orderId)).isZero();
    }

    @Test void anotherOrderCannotReuseAnAlreadyRecordedProviderTransaction() {
        long otherOrder = createOrder(buyer);
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,provider_reference,attempt_key,created_at,updated_at) "
                + "VALUES (?,'ONLINE','PAID',107000,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                otherOrder, "VNPAY:" + transactionNumber(), "historic-" + UUID.randomUUID());
        var before = payment();
        assertThat(payments.acceptIpn(callback()).code()).isEqualTo("99");
        assertThat(payment()).isEqualTo(before);
    }

    @Test void onlyGetCallbacksArePublicAndNoPostCanBypassCsrf() throws Exception {
        mvc.perform(post("/payments/vnpay/ipn")).andExpect(status().isForbidden());
        mvc.perform(get("/payments/vnpay/ipn")).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));
        mvc.perform(get("/payments/vnpay/return")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không thể xác minh")));
        assertUnpaid();
    }

    @Test void initiatingPaymentRequiresBuyerAndUsesExistingUnpaidAttemptWithoutRenewal() throws Exception {
        mvc.perform(get("/orders/{id}/payments/vnpay", orderId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/{id}/payments/vnpay", orderId).with(user(principal(createUser()))))
                .andExpect(status().isNotFound());
        var before = payment();
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/orders/{id}/payments/vnpay", orderId).with(user(principal(buyer))))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(VnpayGateway.SANDBOX_URL)));
        }
        assertThat(payment()).isEqualTo(before);
        jdbc.update("UPDATE uteexpress.payments SET created_at=CURRENT_TIMESTAMP - INTERVAL '16 minutes' WHERE order_id=?", orderId);
        mvc.perform(get("/orders/{id}/payments/vnpay", orderId).with(user(principal(buyer))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Liên kết thanh toán đã hết hạn")));
        assertUnpaid();
    }

    @ParameterizedTest @ValueSource(strings = {"ONLINE", "COD"})
    void realCheckoutCreatesOnePaymentAndReplaysWithoutStockOrCartDuplication(String method) throws Exception {
        String key = UUID.randomUUID().toString();
        long address = jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','DB address',true) RETURNING id", Long.class, buyer);
        long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, buyer, key);
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id", Long.class, key);
        long product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',125000,10,'ACTIVE') RETURNING id", Long.class, shop, category);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id", Long.class, "P" + key.replace("-", "").substring(0, 18).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',17000,true)", provider);
        if (jdbc.queryForObject("SELECT count(*) FROM uteexpress.commission_policies", Integer.class) == 0) {
            jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7.1234,'2020-01-01',?)", buyer);
        }
        authenticate();
        carts.addProduct(new AddCartProductRequest(product, 2));
        mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf())
                .param("addressId", String.valueOf(address)).param("shippingProviderId", String.valueOf(provider))
                .param("shippingServiceCode", "STANDARD"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("VNPAY Sandbox")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"ONLINE\"")));
        authenticate();
        var request = new CheckoutRequest(key, List.of(new CheckoutRequest.Item(product, 2)), address, provider,
                "STANDARD", CheckoutRequest.PaymentMethod.valueOf(method), null);
        var result = placement.placeOrder(request);
        var replay = placement.placeOrder(request);
        assertThat(replay.orderId()).isEqualTo(result.orderId());
        assertThat(replay.replayed()).isTrue();
        var record = jdbc.queryForMap("SELECT * FROM uteexpress.payments WHERE order_id=?", result.orderId());
        assertThat(record).containsEntry("method", method).containsEntry("status", "UNPAID")
                .containsEntry("amount", money("267000.00")).containsEntry("paid_at", null);
        assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product)).isEqualTo(8);
        assertThat(carts.getOrCreateCart().items()).isEmpty();
        clear();
        mvc.perform(get("/user/checkout/view").with(user(principal(buyer))))
                .andExpect(status().isOk()).andExpect(model().attribute("vnpayEnabled", true));
    }

    @Test void concurrentDuplicateIpnsBlockOnTheOrderAndCommitExactlyOnePayment() throws Exception {
        var fields = callback();
        var beforeOrder = order();
        var started = new CountDownLatch(2);
        var pids = new java.util.concurrent.ConcurrentLinkedQueue<Integer>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = transactions.execute(tx -> {
                int holder = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, orderId);
                var workers = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() ->
                        transactions.execute(worker -> {
                            pids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            started.countDown();
                            return payments.acceptIpn(fields);
                        }))).toList();
                try { assertThat(started.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blocked = false;
                while (System.nanoTime() < deadline) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    blocked = pids.stream().allMatch(pid -> Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT (? = ANY(pg_blocking_pids(pid)) OR EXISTS (SELECT 1 FROM unnest(pg_blocking_pids(pid)) b WHERE ? = ANY(pg_blocking_pids(b)))) "
                                    + "AND wait_event_type='Lock' AND query ILIKE '%uteexpress.orders%' FROM pg_stat_activity WHERE pid=?",
                            Boolean.class, holder, holder, pid)));
                    if (blocked) break;
                    Thread.onSpinWait();
                }
                assertThat(blocked).as("both IPNs wait on the actual holder's Order row lock").isTrue();
                return workers;
            });
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS).code(), futures.get(1).get(15, TimeUnit.SECONDS).code()))
                    .containsExactlyInAnyOrder("00", "02");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.payments WHERE order_id=? AND status='PAID'", Integer.class, orderId)).isEqualTo(1);
        assertThat(order()).isEqualTo(beforeOrder);
    }

    private Map<String, String> callback() {
        String reference = (String) payment().get("attempt_key");
        var fields = new TreeMap<>(Map.of("vnp_TmnCode", "TESTCODE", "vnp_TxnRef", reference,
                "vnp_Amount", "10700000", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", transactionNumber(), "vnp_PayDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .withZone(ZoneOffset.ofHours(7)).format(Instant.now())));
        sign(fields); return fields;
    }
    private void sign(Map<String, String> fields) { fields.put("vnp_SecureHash", gateway.signature(fields)); }
    private String transactionNumber() { return "100000" + orderId; }
    private static MockHttpServletRequestBuilder callbackRequest(String endpoint, Map<String, String> fields) {
        var request = get("/payments/vnpay/" + endpoint);
        fields.forEach(request::param); return request;
    }
    private Map<String, Object> payment() { return jdbc.queryForMap("SELECT * FROM uteexpress.payments WHERE order_id=?", orderId); }
    private Map<String, Object> order() { return jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", orderId); }
    private void assertUnpaid() { assertThat(payment()).containsEntry("status", "UNPAID").containsEntry("paid_at", null); }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private long createUser() {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, name + "@example.test", name + "@example.test", name, name);
    }
    private static UteExpressPrincipal principal(long id) {
        return new UteExpressPrincipal(id, "buyer" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
    private void authenticate() {
        var principal = principal(buyer);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
