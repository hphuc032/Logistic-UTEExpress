package com.uteexpress.checkout;

import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shipping.service.ShippingQuoteService;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutQuoteIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired CheckoutQuoteService checkout;
    @Autowired CartService carts;
    @Autowired ShippingQuoteService shipping;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired Flyway flyway;
    long buyer, other, address, otherAddress, shop, category, product, item, provider;
    String username;

    @BeforeEach void fixture() {
        username = "chk" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        buyer = createUser(username);
        other = createUser(username + "b");
        address = createAddress(buyer);
        otherAddress = createAddress(other);
        shop = createShop(buyer, username);
        category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id",
                Long.class, username);
        product = createProduct(shop, "Current product", 125000);
        provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?, 'Provider',true) RETURNING id",
                Long.class, "P" + username.substring(3).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',17000,true)", provider);
        authenticate(buyer);
        item = carts.addProduct(new AddCartProductRequest(product, 2)).items().getFirst().id();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void authenticatedQuoteUsesCurrentServerPriceAndDoesNotWriteAnything() throws Exception {
        jdbc.update("UPDATE uteexpress.products SET price=130000 WHERE id=?", product);
        var before = databaseState();
        clear();
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body(address)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.groups").doesNotExist())
                .andExpect(jsonPath("$.quote.shopId").value(shop))
                .andExpect(jsonPath("$.quote.address.detail").value("DB address"))
                .andExpect(jsonPath("$.quote.items[0].unitPrice").value(130000))
                .andExpect(jsonPath("$.quote.totals.subtotal").value(260000))
                .andExpect(jsonPath("$.quote.totals.shippingFee").value(17000))
                .andExpect(jsonPath("$.quote.totals.grandTotal").value(277000));
        assertThat(databaseState()).isEqualTo(before);
    }
    @Test void sameShopMultipleSelectedProductsReturnOneQuote() throws Exception {
        long second = createProduct(shop, "Second", 100);
        carts.addProduct(new AddCartProductRequest(second, 3));
        var before = databaseState();
        clear();
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body(address)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.groups").doesNotExist())
                .andExpect(jsonPath("$.quote.shopId").value(shop))
                .andExpect(jsonPath("$.quote.items.length()").value(2))
                .andExpect(jsonPath("$.quote.totals.subtotal").value(250300))
                .andExpect(jsonPath("$.quote.totals.shippingFee").value(17000))
                .andExpect(jsonPath("$.quote.totals.grandTotal").value(267300));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test void multipleSelectedShopsAreRejected() throws Exception {
        long otherShop = createShop(other, username + "b");
        long second = createProduct(otherShop, "Other shop product", 200);
        carts.addProduct(new AddCartProductRequest(second, 1));
        var before = databaseState();
        assertThatThrownBy(() -> checkout.quote(request(address)))
                .isInstanceOfSatisfying(ApplicationException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(error.publicMessage()).isEqualTo(ErrorCode.Detail.SINGLE_SHOP_CHECKOUT.message());
                });
        clear();
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body(address)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(ErrorCode.Detail.SINGLE_SHOP_CHECKOUT.message()))
                .andExpect(jsonPath("$.quote").doesNotExist()).andExpect(jsonPath("$.groups").doesNotExist());
        mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf())
                        .param("addressId", Long.toString(address)).param("shippingProviderId", Long.toString(provider))
                        .param("shippingServiceCode", "STANDARD"))
                .andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attribute("errorMessage", ErrorCode.Detail.SINGLE_SHOP_CHECKOUT.message()));
        assertThat(databaseState()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"unknownService", "inactiveRate", "inactiveProvider", "destination"})
    void invalidShippingServiceIsRejected(String invalid) throws Exception {
        String service = "STANDARD";
        switch (invalid) {
            case "unknownService" -> service = "UNKNOWN";
            case "inactiveRate" -> jdbc.update("UPDATE uteexpress.shipping_rates SET active=false WHERE provider_id=?", provider);
            case "inactiveProvider" -> jdbc.update("UPDATE uteexpress.shipping_providers SET active=false WHERE id=?", provider);
            case "destination" -> jdbc.update("UPDATE uteexpress.addresses SET province_code='UNSUPPORTED' WHERE id=?", address);
        }
        var before = databaseState();
        clear();
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body(address).replace("STANDARD", service)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.quote").doesNotExist());
        assertThat(databaseState()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"MODERATED", "SUSPENDED"})
    void moderationBlocksSelectedProductWithoutMutation(String state) throws Exception {
        if (state.equals("MODERATED")) {
            jdbc.update("UPDATE uteexpress.products SET status='MODERATED',moderation_reason='Test moderation' WHERE id=?", product);
        } else {
            jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='Test suspension' WHERE id=?", shop);
        }
        var before = databaseState();
        clear();
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body(address)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.quote").doesNotExist());
        assertThat(databaseState()).isEqualTo(before);
    }
    @Test void foreignAddressAndForeignAddressOptionsAreRejected() throws Exception {
        assertThatThrownBy(() -> checkout.quote(request(otherAddress))).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> checkout.shippingOptions(otherAddress)).isInstanceOf(ApplicationException.class);
        clear();
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body(otherAddress))).andExpect(status().isNotFound());
        mvc.perform(get("/user/checkout/view").with(user(principal(buyer))).param("addressId", Long.toString(otherAddress)))
                .andExpect(redirectedUrl("/user/checkout/view"));
    }
    @Test void userBCannotCheckoutUserACartEvenWithValidOwnAddress() {
        authenticate(other);
        assertThatThrownBy(() -> checkout.quote(request(otherAddress))).isInstanceOf(ApplicationException.class);
        var own = carts.addProduct(new AddCartProductRequest(product, 1));
        var preview = checkout.quote(request(otherAddress));
        assertThat(preview.quote().items().getFirst().quantity()).isEqualTo(1);
        assertThat(own.items().getFirst().id()).isNotEqualTo(item);
        authenticate(buyer);
        assertThat(carts.getCurrentUserCart().orElseThrow().items().getFirst().quantity()).isEqualTo(2);
    }
    @Test void emptyCartAndNoSelectedItemsFailWithoutMutation() {
        carts.selectItem(item, new SelectCartItemRequest(false));
        var before = databaseState();
        assertThatThrownBy(() -> checkout.quote(request(address))).isInstanceOf(ApplicationException.class);
        assertThat(databaseState()).isEqualTo(before);
        carts.removeItem(item);
        assertThatThrownBy(() -> checkout.quote(request(address))).isInstanceOf(ApplicationException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"hidden", "stock", "outOfStock", "shop", "category"})
    void anyInvalidSelectedItemRejectsWholeQuote(String invalid) {
        long valid = createProduct(shop, "Valid", 100);
        carts.addProduct(new AddCartProductRequest(valid, 1));
        switch (invalid) {
            case "hidden" -> jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", product);
            case "stock" -> jdbc.update("UPDATE uteexpress.products SET stock=1 WHERE id=?", product);
            case "outOfStock" -> jdbc.update("UPDATE uteexpress.products SET stock=0 WHERE id=?", product);
            case "shop" -> jdbc.update("UPDATE uteexpress.shops SET status='REJECTED',rejection_reason='Test rejection' WHERE id=?", shop);
            case "category" -> jdbc.update("UPDATE uteexpress.categories SET active=false WHERE id=?", category);
        }
        var before = databaseState();
        assertThatThrownBy(() -> checkout.quote(request(address))).isInstanceOf(ApplicationException.class);
        assertThat(databaseState()).isEqualTo(before);
    }
    @Test void unselectedUnavailableItemDoesNotEnterQuote() {
        long unselected = createProduct(createShop(other, username + "b"), "Unavailable", 100);
        long unselectedItem = carts.addProduct(new AddCartProductRequest(unselected, 1)).items().getLast().id();
        carts.selectItem(unselectedItem, new SelectCartItemRequest(false));
        jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", unselected);
        assertThat(checkout.quote(request(address)).quote().items())
                .extracting(CheckoutQuote.ItemSnapshot::productId).containsExactly(product);
    }
    @ParameterizedTest @ValueSource(strings = {"price", "subtotal", "shippingFee", "total", "grandTotal", "owner", "userId", "shopId", "cartItemId", "items", "quantity", "detail"})
    void jsonRejectsInjectionAndFormCannotBindIt(String field) throws Exception {
        clear();
        String body = body(address);
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf()).contentType("application/json")
                        .content(body.substring(0, body.length() - 1) + ",\"" + field + "\":1}"))
                .andExpect(status().isBadRequest());
        var before = databaseState();
        var result = mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf())
                        .param("addressId", Long.toString(address)).param("shippingProviderId", Long.toString(provider))
                        .param("shippingServiceCode", "STANDARD").param(field, "1"))
                .andExpect(status().isOk()).andReturn();
        CheckoutPreview preview = (CheckoutPreview) result.getModelAndView().getModel().get("preview");
        assertThat(preview.quote().totals().grandTotal()).isEqualByComparingTo("267000");
        assertThat(databaseState()).isEqualTo(before);
    }
    @Test void unauthenticatedAndCsrfRequestsBlocked() throws Exception {
        clear();
        mvc.perform(post("/user/checkout/quote").with(csrf()).contentType("application/json").content(body(address)))
                .andExpect(status().isUnauthorized());
        for (String route : List.of("/user/checkout/quote", "/user/checkout/view")) {
            mvc.perform(post(route).with(user(principal(buyer))).contentType("application/json").content(body(address)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/user/checkout/view")).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"addressId\":0}", "{\"addressId\":1,\"shippingProviderId\":0,\"shippingServiceCode\":\"bad\"}"})
    void beanValidationRejectsMalformedSelection(String body) throws Exception {
        clear();
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void shippingOptionsFilterInactiveAndUnsupportedRoutesAndQuotesRefreshFee() {
        assertThat(checkout.shippingOptions(address)).anyMatch(option -> option.providerId().equals(provider));
        jdbc.update("UPDATE uteexpress.shipping_rates SET fee=21000 WHERE provider_id=?", provider);
        assertThat(checkout.quote(request(address)).quote().totals().shippingFee()).isEqualByComparingTo("21000");
        jdbc.update("UPDATE uteexpress.shipping_rates SET active=false WHERE provider_id=?", provider);
        assertThat(checkout.shippingOptions(address)).noneMatch(option -> option.providerId().equals(provider));
        assertThatThrownBy(() -> checkout.quote(request(address))).isInstanceOf(ApplicationException.class);
        jdbc.update("UPDATE uteexpress.shipping_rates SET active=true WHERE provider_id=?", provider);
        jdbc.update("UPDATE uteexpress.shipping_providers SET active=false WHERE id=?", provider);
        assertThat(checkout.shippingOptions(address)).noneMatch(option -> option.providerId().equals(provider));
        assertThatThrownBy(() -> checkout.quote(request(address))).isInstanceOf(ApplicationException.class);
        assertThat(shipping.availableServices("UNSUPPORTED")).isEmpty();
        assertThatThrownBy(() -> shipping.availableServices("bad region")).isInstanceOf(ApplicationException.class);
    }
    @Test void htmlEscapesProductAndAddressAndHasOnlyPreviewActions() throws Exception {
        jdbc.update("UPDATE uteexpress.products SET name='<script>bad()</script>' WHERE id=?", product);
        jdbc.update("UPDATE uteexpress.addresses SET detail='<script>address()</script>' WHERE id=?", address);
        clear();
        var result = mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf())
                        .param("addressId", Long.toString(address)).param("shippingProviderId", Long.toString(provider))
                        .param("shippingServiceCode", "STANDARD")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("&lt;script&gt;", "table-responsive", "name=\"_csrf\"")
                .doesNotContain("<script>bad()", "<script>address()", "place-order");
    }
    @Test void emptyAndBusinessErrorPagesRenderSafely() throws Exception {
        jdbc.update("DELETE FROM uteexpress.addresses WHERE user_id=?", other);
        clear();
        mvc.perform(get("/user/checkout/view").with(user(principal(other))))
                .andExpect(status().isOk()).andExpect(view().name("checkout/quote"));
        mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf()))
                .andExpect(redirectedUrl("/user/checkout/view")).andExpect(flash().attributeExists("errorMessage"));
        authenticate(buyer);
        carts.selectItem(item, new SelectCartItemRequest(false));
        clear();
        mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf())
                        .param("addressId", Long.toString(address)).param("shippingProviderId", Long.toString(provider))
                        .param("shippingServiceCode", "STANDARD"))
                .andExpect(redirectedUrl("/user/checkout/view")).andExpect(flash().attributeExists("errorMessage"));
    }
    @Test void unsupportedAddressCodeRendersEmptyShippingStateWithoutRedirectLoop() throws Exception {
        jdbc.update("UPDATE uteexpress.addresses SET province_code='Unsupported region' WHERE id=?", address);
        clear();
        mvc.perform(get("/user/checkout/view").with(user(principal(buyer))))
                .andExpect(status().isOk()).andExpect(view().name("checkout/quote"))
                .andExpect(model().attributeExists("errorMessage"));
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER", "SHIPPER"})
    void unrelatedRolesCannotAccessBuyerCheckout(String role) throws Exception {
        clear();
        mvc.perform(post("/user/checkout/quote").with(user("wrong-role").roles(role)).with(csrf())
                        .contentType("application/json").content(body(address)))
                .andExpect(status().isForbidden());
    }
    @Test void realLogoutLoginChangesOwnershipAndRevokesOldCookie() throws Exception {
        clear();
        Cookie first = login(username);
        mvc.perform(post("/user/checkout/quote").cookie(first).with(csrf()).contentType("application/json").content(body(address)))
                .andExpect(status().isOk());
        mvc.perform(post("/logout").cookie(first).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/user/checkout/quote").cookie(first).with(csrf()).contentType("application/json").content(body(address)))
                .andExpect(status().isUnauthorized());
        Cookie second = login(username + "b");
        mvc.perform(post("/user/checkout/quote").cookie(second).with(csrf()).contentType("application/json").content(body(address)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/user/checkout/quote").cookie(second).with(csrf()).contentType("application/json").content(body(otherAddress)))
                .andExpect(status().isBadRequest());
    }
    @Test void flywayAndQuoteLocksAreReleasedBeforeReturn() {
        checkout.quote(request(address));
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        // A later quote sees changed stock, proving the first quote created no reservation/guarantee.
        jdbc.update("UPDATE uteexpress.products SET stock=1 WHERE id=?", product);
        assertThatThrownBy(() -> checkout.quote(request(address))).isInstanceOf(ApplicationException.class);
    }
    private Map<String, List<Map<String, Object>>> databaseState() {
        Map<String, List<Map<String, Object>>> state = new TreeMap<>();
        for (String table : List.of("orders", "order_items", "payments", "products", "carts", "cart_items")) {
            state.put(table, jdbc.queryForList("SELECT * FROM uteexpress." + table + " ORDER BY id"));
        }
        return state;
    }
    private QuoteRequest request(long addressId) { return new QuoteRequest(addressId, provider, "STANDARD"); }
    private String body(long addressId) {
        return "{\"addressId\":" + addressId + ",\"shippingProviderId\":" + provider + ",\"shippingServiceCode\":\"STANDARD\"}";
    }
    private long createUser(String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name, passwords.encode("CheckoutTest123!"));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='USER'", id);
        return id;
    }
    private long createAddress(long owner) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default)
                VALUES (?,'Receiver','0900000000','VN','District','DB address',true) RETURNING id
                """, Long.class, owner);
    }
    private long createShop(long owner, String slug) {
        return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id",
                Long.class, owner, slug);
    }
    private long createProduct(long shopId, String name, int price) {
        return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,?,?,10,'ACTIVE') RETURNING id",
                Long.class, shopId, category, name, price);
    }
    private Cookie login(String name) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("identifier", name).param("password", "CheckoutTest123!"))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }
    private static UteExpressPrincipal principal(long id) {
        return new UteExpressPrincipal(id, "buyer" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
    private static void authenticate(long id) {
        var principal = principal(id);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
