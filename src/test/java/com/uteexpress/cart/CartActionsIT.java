package com.uteexpress.cart;

import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CartActionsIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired CartService carts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    long buyer, other, product, shop, category, item;
    String username;

    @BeforeEach
    void fixture() {
        username = "cart" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        buyer = createUser(username);
        other = createUser(username + "b");
        shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status)
                VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id
                """, Long.class, buyer, username);
        category = jdbc.queryForObject("""
                INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id
                """, Long.class, username);
        product = createProduct("Product", 125000, 10);
        authenticate(buyer);
        item = carts.addProduct(new AddCartProductRequest(product, 2)).items().getFirst().id();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void updatePersistsAndNeverReservesInventory() {
        CartView result = carts.updateQuantity(item, new UpdateCartQuantityRequest(10));
        assertThat(result.subtotal()).isEqualByComparingTo("1250000");
        assertThat(current().items().getFirst().quantity()).isEqualTo(10);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void addRejectsAccumulatedQuantityAboveStockWithoutMutation() {
        assertError(() -> carts.addProduct(new AddCartProductRequest(product, 9)), ErrorCode.CONFLICT);
        assertThat(current().items().getFirst().quantity()).isEqualTo(2);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void missingAndNullActionFieldsAreRejectedByBeanValidation() throws Exception {
        clear();
        for (String action : List.of("quantity", "selection")) {
            String field = action.equals("quantity") ? "quantity" : "selected";
            for (String body : List.of("{}", "{\"" + field + "\":null}")) {
                mvc.perform(post("/user/cart/items/{id}/" + action, item).with(user(principal(buyer))).with(csrf())
                                .contentType("application/json").content(body))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            }
        }
    }

    @Test
    void unavailableProductNameIsEscapedInHtml() throws Exception {
        jdbc.update("UPDATE uteexpress.products SET name=?,status='HIDDEN' WHERE id=?", "<script>alert(1)</script>", product);
        clear();
        String html = mvc.perform(get("/user/cart/view").with(user(principal(buyer))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>alert(1)</script>");
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 11})
    void rejectsInvalidQuantitiesWithoutChangingLine(int quantity) {
        assertError(() -> carts.updateQuantity(item, new UpdateCartQuantityRequest(quantity)),
                quantity <= 0 ? ErrorCode.VALIDATION_FAILED : ErrorCode.CONFLICT);
        assertThat(current().items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void removeKeepsEmptyCartAndUpdatesSummary() {
        Long cartId = current().id();
        CartView removed = carts.removeItem(item);
        assertThat(removed.items()).isEmpty();
        assertThat(removed.subtotal()).isZero();
        assertThat(current().id()).isEqualTo(cartId);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectionPersistsAcrossTransactions(boolean selected) {
        carts.selectItem(item, new SelectCartItemRequest(!selected));
        carts.selectItem(item, new SelectCartItemRequest(selected));
        assertThat(current().items().getFirst().selected()).isEqualTo(selected);
        assertThat(jdbc.queryForObject("SELECT selected FROM uteexpress.cart_items WHERE id=?", Boolean.class, item))
                .isEqualTo(selected);
        assertThat(current().selectedSubtotal()).isEqualByComparingTo(selected ? "250000" : "0");
    }

    @ParameterizedTest @ValueSource(strings = {"quantity", "remove", "selection"})
    void crossOwnerActionsAreRejectedByServiceAndHttp(String action) throws Exception {
        authenticate(other);
        carts.getOrCreateCart(); // Also exercise ownership when the attacker already has a cart.
        switch (action) {
            case "quantity" -> assertError(() -> carts.updateQuantity(item, new UpdateCartQuantityRequest(3)), ErrorCode.RESOURCE_NOT_FOUND);
            case "remove" -> assertError(() -> carts.removeItem(item), ErrorCode.RESOURCE_NOT_FOUND);
            default -> assertError(() -> carts.selectItem(item, new SelectCartItemRequest(false)), ErrorCode.RESOURCE_NOT_FOUND);
        }
        clear();
        mvc.perform(post("/user/cart/items/{id}/" + action, item).with(user(principal(other))).with(csrf())
                        .contentType("application/json").content(action.equals("quantity") ? "{\"quantity\":3}" : "{\"selected\":false}"))
                .andExpect(status().isNotFound());
        authenticate(buyer);
        assertThat(current().items().getFirst().quantity()).isEqualTo(2);
        assertThat(current().items().getFirst().selected()).isTrue();
    }

    @Test
    void totalsUseCurrentPricesAndExcludeUnselectedFromSelectedTotal() throws Exception {
        long second = createProduct("Second", 100, 20);
        carts.addProduct(new AddCartProductRequest(second, 3));
        carts.selectItem(item, new SelectCartItemRequest(false));
        jdbc.update("UPDATE uteexpress.products SET price=90000 WHERE id=?", product);
        assertThat(current().subtotal()).isEqualByComparingTo("180300");
        assertThat(current().selectedSubtotal()).isEqualByComparingTo("300");
        clear();
        mvc.perform(get("/user/cart").with(user(principal(buyer))))
                .andExpect(jsonPath("$.subtotal").value(180300)).andExpect(jsonPath("$.selectedSubtotal").value(300));
    }

    @ParameterizedTest @ValueSource(strings = {"price", "subtotal", "total", "owner", "userId", "cartId"})
    void jsonRejectsTamperingAndFormsNeverBindIt(String field) throws Exception {
        clear();
        for (String action : List.of("quantity", "selection")) {
            String body = action.equals("quantity") ? "\"quantity\":3" : "\"selected\":false";
            mvc.perform(post("/user/cart/items/{id}/" + action, item).with(user(principal(buyer))).with(csrf())
                            .contentType("application/json").content("{" + body + ",\"" + field + "\":1}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/user/cart/view/items/{id}/quantity", item).with(user(principal(buyer))).with(csrf())
                        .param("quantity", "3").param(field, "1"))
                .andExpect(redirectedUrl("/user/cart/view"));
        authenticate(buyer);
        assertThat(current().items().getFirst().quantity()).isEqualTo(3);
        assertThat(current().subtotal()).isEqualByComparingTo("375000");
    }

    @ParameterizedTest @ValueSource(strings = {"hidden", "category", "shop", "empty", "low"})
    void invalidProductDoesNotBreakCartAndCanBeRemoved(String reason) throws Exception {
        long valid = createProduct("Valid product", 100, 10);
        carts.addProduct(new AddCartProductRequest(valid, 1));
        switch (reason) {
            case "hidden" -> jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", product);
            case "category" -> jdbc.update("UPDATE uteexpress.categories SET active=false WHERE id=?", category);
            case "shop" -> jdbc.update("UPDATE uteexpress.shops SET status='PENDING' WHERE id=?", shop);
            case "empty" -> jdbc.update("UPDATE uteexpress.products SET stock=0 WHERE id=?", product);
            default -> jdbc.update("UPDATE uteexpress.products SET stock=1 WHERE id=?", product);
        }
        CartView result = current();
        assertThat(result.items()).hasSize(2);
        CartItemView invalid = result.items().getFirst();
        assertThat(invalid.productName()).isEqualTo("Product");
        assertThat(invalid.available()).isFalse();
        assertThat(invalid.subtotal()).isZero();
        boolean sharedUnavailable = reason.equals("category") || reason.equals("shop");
        assertThat(result.subtotal()).isEqualByComparingTo(sharedUnavailable ? "0" : "100");
        assertThat(result.selectedSubtotal()).isEqualByComparingTo(sharedUnavailable ? "0" : "100");
        assertThat(invalid.status()).isEqualTo(reason.equals("empty") ? CartItemStatus.OUT_OF_STOCK
                : reason.equals("low") ? CartItemStatus.INSUFFICIENT_STOCK : CartItemStatus.UNAVAILABLE);
        assertError(() -> carts.updateQuantity(item, new UpdateCartQuantityRequest(2)),
                reason.equals("empty") || reason.equals("low") ? ErrorCode.CONFLICT : ErrorCode.RESOURCE_NOT_FOUND);
        clear();
        mvc.perform(get("/user/cart/view").with(user(principal(buyer))))
                .andExpect(status().isOk()).andExpect(content().string(containsString(
                        reason.equals("empty") ? "Hết hàng" : reason.equals("low") ? "Vượt tồn kho" : "Không còn bán")));
        authenticate(buyer);
        carts.selectItem(item, new SelectCartItemRequest(false));
        assertThat(carts.removeItem(item).items()).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"quantity", "remove", "selection"})
    void everyActionRequiresCsrfForJsonAndForms(String action) throws Exception {
        clear();
        for (String prefix : List.of("/user/cart", "/user/cart/view")) {
            mvc.perform(post(prefix + "/items/{id}/" + action, item).with(user(principal(buyer)))
                            .contentType("application/json").content("{\"quantity\":3,\"selected\":false}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void formsRenderAndUsePrgForSelectionQuantityErrorsAndRemoval() throws Exception {
        clear();
        mvc.perform(get("/user/cart/view").with(user(principal(buyer))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Product")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Giỏ hàng trống"))));
        mvc.perform(post("/user/cart/view/items/{id}/selection", item).with(user(principal(buyer))).with(csrf())
                        .param("_selected", "on"))
                .andExpect(redirectedUrl("/user/cart/view")).andExpect(flash().attributeExists("successMessage"));
        authenticate(buyer);
        assertThat(current().items().getFirst().selected()).isFalse();
        clear();
        mvc.perform(post("/user/cart/view/items/{id}/selection", item).with(user(principal(buyer))).with(csrf())
                        .param("_selected", "on").param("selected", "true"))
                .andExpect(redirectedUrl("/user/cart/view"));
        for (String quantity : List.of("0", "-1", "bad", "11")) {
            mvc.perform(post("/user/cart/view/items/{id}/quantity", item).with(user(principal(buyer))).with(csrf())
                            .param("quantity", quantity))
                    .andExpect(redirectedUrl("/user/cart/view")).andExpect(flash().attributeExists("errorMessage"));
        }
        mvc.perform(post("/user/cart/view/items/{id}/remove", item).with(user(principal(buyer))).with(csrf()))
                .andExpect(redirectedUrl("/user/cart/view"));
        mvc.perform(get("/user/cart/view").with(user(principal(buyer))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Giỏ hàng trống")));
    }

    @Test
    void cartAndSelectionSurviveRealLogoutLogin() throws Exception {
        carts.selectItem(item, new SelectCartItemRequest(false));
        Long cartId = current().id();
        clear();
        Cookie first = login();
        mvc.perform(post("/logout").cookie(first).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/user/cart")).andExpect(status().isUnauthorized());
        Cookie second = login();
        mvc.perform(get("/user/cart").cookie(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId)).andExpect(jsonPath("$.items[0].id").value(item))
                .andExpect(jsonPath("$.items[0].selected").value(false)).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    // csrf() in other methods replaces the shared filter's cookie repository with a test session repository.
    // Reload the production filter chain before exercising actual browser cookies and rendered form tokens.
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void realCsrfCookieSurvivesJwtAssetRequestsAndProtectsFormSubmissions() throws Exception {
        clear();
        var loginPage = mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn();
        Cookie anonymousCsrf = loginPage.getResponse().getCookie("XSRF-TOKEN");
        var loggedIn = mvc.perform(post("/login").cookie(anonymousCsrf)
                        .param("_csrf", csrfValue(loginPage)).param("identifier", username).param("password", "CartTest123!"))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0)).andReturn();
        Cookie jwt = loggedIn.getResponse().getCookie("UTEEXPRESS_AUTH");
        var cartPage = mvc.perform(get("/user/cart/view").cookie(jwt)).andExpect(status().isOk()).andReturn();
        Cookie authenticatedCsrf = cartPage.getResponse().getCookie("XSRF-TOKEN");
        assertThat(authenticatedCsrf).isNotNull();
        assertThat(authenticatedCsrf.getValue()).isNotEqualTo(anonymousCsrf.getValue());
        for (String path : List.of("/css/app.css", "/js/app.js", "/user/cart/view")) {
            mvc.perform(get(path).cookie(jwt, authenticatedCsrf)).andExpect(status().isOk())
                    .andExpect(cookie().doesNotExist("XSRF-TOKEN"));
        }
        String action = "/user/cart/view/items/" + item + "/selection";
        mvc.perform(post(action).cookie(jwt, authenticatedCsrf).param("selected", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(post(action).cookie(jwt, authenticatedCsrf).param("selected", "false").param("_csrf", "invalid"))
                .andExpect(status().isForbidden());
        var saved = mvc.perform(post(action).cookie(jwt, authenticatedCsrf)
                        .param("_csrf", csrfValue(cartPage)).param("selected", "false"))
                .andExpect(redirectedUrl("/user/cart/view")).andReturn();
        assertThat(jdbc.queryForObject("SELECT selected FROM uteexpress.cart_items WHERE id=?", Boolean.class, item))
                .isFalse();
        mvc.perform(get("/user/cart/view").cookie(jwt, authenticatedCsrf)
                        .session((org.springframework.mock.web.MockHttpSession) saved.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đã lưu lựa chọn.")));
        mvc.perform(post("/logout").cookie(jwt, authenticatedCsrf)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").cookie(jwt, authenticatedCsrf).param("_csrf", "invalid"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").cookie(jwt, authenticatedCsrf).param("_csrf", csrfValue(cartPage)))
                .andExpect(redirectedUrl("/login?logout=true")).andExpect(cookie().maxAge("XSRF-TOKEN", 0))
                .andExpect(cookie().maxAge("UTEEXPRESS_AUTH", 0));
        mvc.perform(get("/user/cart").cookie(jwt)).andExpect(status().isUnauthorized());
    }

    private static String csrfValue(org.springframework.test.web.servlet.MvcResult result) throws java.io.UnsupportedEncodingException {
        var matcher = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
                .matcher(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(matcher.find()).as("Rendered form contains a CSRF token").isTrue();
        return matcher.group(1);
    }

    @Test
    void concurrentAddUpdateAndSelectionDoNotLoseIndependentChanges() throws Exception {
        try (var executor = Executors.newFixedThreadPool(3)) {
            CountDownLatch start = new CountDownLatch(1);
            var futures = java.util.stream.IntStream.range(0, 3).mapToObj(index -> executor.submit(() -> {
                authenticate(buyer);
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                    if (index == 0) carts.updateQuantity(item, new UpdateCartQuantityRequest(5));
                    else if (index == 1) carts.addProduct(new AddCartProductRequest(product, 1));
                    else carts.selectItem(item, new SelectCartItemRequest(false));
                } catch (InterruptedException e) { throw new RuntimeException(e); }
                finally { clear(); }
            })).toList();
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertThat(current().items()).hasSize(1);
        assertThat(current().items().getFirst().quantity()).isIn(5, 6); // Serialized absolute assignment or subsequent add.
        assertThat(current().items().getFirst().selected()).isFalse();
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void updateWaitsForStockWriterAndRejectsNewInsufficientStock() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("UPDATE uteexpress.products SET stock=1,version=version+1 WHERE id=?", product);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> update = executor.submit(() -> {
                authenticate(buyer);
                attempted.countDown();
                try { assertError(() -> carts.updateQuantity(item, new UpdateCartQuantityRequest(5)), ErrorCode.CONFLICT); }
                finally { clear(); }
            });
            assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
            try { assertThatThrownBy(() -> update.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            writer.get(20, TimeUnit.SECONDS);
            update.get(20, TimeUnit.SECONDS);
        }
        assertThat(current().items().getFirst().quantity()).isEqualTo(2);
        assertThat(current().items().getFirst().status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);
    }

    private CartView current() { return carts.getCurrentUserCart().orElseThrow(); }
    private int stock() { return jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product); }
    private long createProduct(String name, int price, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status)
                VALUES (?,?,?,?,?,'ACTIVE') RETURNING id
                """, Long.class, shop, category, name, price, stock);
    }
    private long createUser(String name) {
        long id = jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name, passwords.encode("CartTest123!"));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='USER'", id);
        return id;
    }
    private Cookie login() throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("identifier", username).param("password", "CartTest123!"))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }
    private static UteExpressPrincipal principal(long id) {
        return new UteExpressPrincipal(id, "cart" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
    private static void authenticate(long id) {
        var principal = principal(id);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApplicationException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
