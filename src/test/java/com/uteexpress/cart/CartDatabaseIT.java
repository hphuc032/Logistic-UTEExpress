package com.uteexpress.cart;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.dto.CartView;
import com.uteexpress.cart.repository.CartItemRepository;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CartDatabaseIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired CartService carts;
    @Autowired CartItemRepository items;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;
    long buyer;
    long other;
    long product;

    @BeforeEach
    void fixture() {
        buyer = createUser();
        other = createUser();
        String suffix = UUID.randomUUID().toString();
        long shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status)
                VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id
                """, Long.class, buyer, "cart-shop-" + suffix);
        long category = jdbc.queryForObject("""
                INSERT INTO uteexpress.categories(name,slug,active)
                VALUES ('Category',?,true) RETURNING id
                """, Long.class, "cart-category-" + suffix);
        product = jdbc.queryForObject("""
                INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status)
                VALUES (?,?,'Product',125000,10,'ACTIVE') RETURNING id
                """, Long.class, shop, category);
        authenticate(buyer);
    }

    @AfterEach
    void clearPrincipal() { SecurityContextHolder.clearContext(); }

    @Test
    void createsOneCartAndReturnsCurrentAuthenticatedCart() {
        assertThat(carts.getCurrentUserCart()).isEmpty();
        CartView first = carts.getOrCreateCart();
        assertThat(carts.getOrCreateCart().id()).isEqualTo(first.id());
        assertThat(carts.getCurrentUserCart().orElseThrow()).isEqualTo(first);
        assertThat(first.items()).isEmpty();
    }

    @Test
    void firstAddCreatesItemAndSecondAddIncrementsWithoutDuplicate() {
        CartView first = carts.addProduct(new AddCartProductRequest(product, 2));
        CartView second = carts.addProduct(new AddCartProductRequest(product, 3));
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().getFirst().id()).isEqualTo(first.items().getFirst().id());
        assertThat(second.items().getFirst().quantity()).isEqualTo(5);
        assertThat(second.items().getFirst().selected()).isTrue();
        assertThat(second.subtotal()).isEqualByComparingTo("625000");
    }

    @Test
    void rejectsInvalidQuantityAndOverflowWithoutChangingCart() {
        for (Integer quantity : new Integer[]{null, 0, -1}) {
            assertError(() -> carts.addProduct(new AddCartProductRequest(product, quantity)),
                    ErrorCode.VALIDATION_FAILED);
        }
        assertThat(carts.getCurrentUserCart()).isEmpty();
        carts.addProduct(new AddCartProductRequest(product, Integer.MAX_VALUE));
        assertError(() -> carts.addProduct(new AddCartProductRequest(product, 1)), ErrorCode.VALIDATION_FAILED);
        assertThat(carts.getCurrentUserCart().orElseThrow().items().getFirst().quantity())
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void missingOrUnavailableProductIsRejectedWithoutCreatingCart() {
        assertError(() -> carts.addProduct(new AddCartProductRequest(Long.MAX_VALUE, 1)),
                ErrorCode.RESOURCE_NOT_FOUND);
        jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", product);
        assertError(() -> carts.addProduct(new AddCartProductRequest(product, 1)), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(carts.getCurrentUserCart()).isEmpty();
    }

    @Test
    void currentCatalogPriceIsUsedAndInventoryIsNotReserved() {
        carts.addProduct(new AddCartProductRequest(product, 2));
        jdbc.update("UPDATE uteexpress.products SET price=99000 WHERE id=?", product);
        CartView current = carts.getCurrentUserCart().orElseThrow();
        assertThat(current.items().getFirst().unitPrice()).isEqualByComparingTo("99000");
        assertThat(current.subtotal()).isEqualByComparingTo("198000");
        assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product))
                .isEqualTo(10);
    }

    @Test
    void otherBuyerCannotReadOrModifyOwnersItems() {
        CartView owned = carts.addProduct(new AddCartProductRequest(product, 2));
        authenticate(other);
        assertThat(carts.getCurrentUserCart()).isEmpty();
        assertThat(items.findByIdAndCartUserId(owned.items().getFirst().id(), other)).isEmpty();
        assertThat(items.findAllByCartIdAndCartUserIdOrderById(owned.id(), other)).isEmpty();
        assertThat(items.findByCartIdAndProductIdAndCartUserId(owned.id(), product, other)).isEmpty();
        CartView own = carts.addProduct(new AddCartProductRequest(product, 7));
        assertThat(own.id()).isNotEqualTo(owned.id());
        authenticate(buyer);
        assertThat(carts.getCurrentUserCart().orElseThrow().items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void databaseEnforcesUniqueOwnersProductsQuantityAndForeignKeys() {
        CartView cart = carts.addProduct(new AddCartProductRequest(product, 1));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.carts(user_id) VALUES (?)", buyer))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO uteexpress.cart_items(cart_id,product_id,quantity) VALUES (?,?,1)
                """, cart.id(), product)).isInstanceOf(DataIntegrityViolationException.class);
        for (Integer quantity : new Integer[]{0, -1, null}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.cart_items SET quantity=? WHERE cart_id=?",
                    quantity, cart.id())).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.carts(user_id) VALUES (?)", Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO uteexpress.cart_items(cart_id,product_id,quantity) VALUES (?,?,1)
                """, cart.id(), Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM uteexpress.products WHERE id=?", product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentFirstAddsProduceOneCartAndOneItemWithoutLostQuantity() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            CountDownLatch ready = new CountDownLatch(4);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CartView>> futures = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
                    executor.submit(() -> {
                        authenticate(buyer);
                        ready.countDown();
                        try {
                            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                            return carts.addProduct(new AddCartProductRequest(product, 1));
                        } finally { SecurityContextHolder.clearContext(); }
                    })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<CartView> future : futures) future.get(20, TimeUnit.SECONDS);
        }
        CartView result = carts.getCurrentUserCart().orElseThrow();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().quantity()).isEqualTo(4);
    }

    @Test
    void httpUsesPrincipalRejectsUntrustedFieldsAndProtectsCsrf() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/user/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/user/cart/items").with(user(principal(buyer)))
                        .contentType("application/json").content(body()))
                .andExpect(status().isForbidden());
        for (String extra : List.of("userId", "owner", "price", "subtotal", "cartId", "selected")) {
            mvc.perform(post("/user/cart/items").with(user(principal(buyer))).with(csrf())
                            .contentType("application/json")
                            .content("{\"productId\":" + product + ",\"quantity\":2,\"" + extra + "\":1}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/user/cart/items").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(250000));
        mvc.perform(get("/user/cart").with(user(principal(other))).param("userId", "" + buyer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(post("/user/cart/items").with(user(principal(buyer))).with(csrf())
                        .contentType("application/json").content("{\"productId\":" + product + ",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cleanMigrationValidatesAndSecondRunHasNoWork() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private String body() { return "{\"productId\":" + product + ",\"quantity\":2}"; }

    private long createUser() {
        String name = "cart" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status)
                VALUES (?,?,?,?,'test-fixture-not-a-password','ACTIVE') RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name);
    }

    private static UteExpressPrincipal principal(long id) {
        return new UteExpressPrincipal(id, "cart" + id, null, 0,
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
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
