package com.uteexpress.shop;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.security.RoleCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shop.dto.ShopRegistrationRequest;
import com.uteexpress.shop.service.ShopRegistrationService;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShopRegistrationIT {
    private static final String RAW_PASSWORD = "RawSecret1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired ShopRegistrationService shops;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from uteexpress.shops");
        jdbc.update("delete from uteexpress.otp_tokens");
        jdbc.update("delete from uteexpress.user_roles");
        jdbc.update("delete from uteexpress.users");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void migrationCreatesValidatedShopContractAndSecondMigrationHasNoWork() {
        Integer tableCount = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = 'uteexpress' and table_name = 'shops'
                """, Integer.class);
        List<String> constraints = jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                 where table_schema = 'uteexpress' and table_name = 'shops'
                """, String.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(constraints).contains(
                "pk_shops", "fk_shops_owner_id", "uq_shops_owner_id", "uq_shops_slug",
                "ck_shops_name_not_blank", "ck_shops_slug_canonical",
                "ck_shops_pickup_address_not_blank", "ck_shops_status",
                "ck_shops_rejection_reason", "ck_shops_version_nonnegative");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void databaseEnforcesOwnerSlugStatusVersionAndForeignKeyConstraints() {
        long owner1 = createUser("owner1", "USER");
        long owner2 = createUser("owner2", "USER");
        long owner3 = createUser("owner3", "USER");
        long owner4 = createUser("owner4", "USER");

        insertShop(owner1, "alpha", "PENDING");
        assertThat(jdbc.queryForObject("select version from uteexpress.shops where owner_id = ?", Long.class, owner1))
                .isZero();

        assertThatThrownBy(() -> insertShop(owner1, "second-owner-shop", "PENDING"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertShop(owner2, "alpha", "PENDING"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertShop(owner2, "ABC SHOP", "PENDING"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertShop(owner3, "valid-status-test", "SUSPENDED"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertShop(999_999_999L, "missing-owner", "PENDING"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into uteexpress.shops
                    (owner_id, name, slug, pickup_address, status, version)
                values (?, 'Shop', 'negative-version', 'Pickup', 'PENDING', -1)
                """, owner4)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void realJwtAndCsrfCreatePendingShopForPrincipalWithoutRoleEscalation() throws Exception {
        long userId = createUser("jwt-owner", "USER");
        long spoofedOwner = createUser("spoof-target", "USER");
        Cookie jwt = login("jwt-owner");

        mvc.perform(post("/user/shop/register").cookie(jwt)
                        .param("name", "JWT Shop")
                        .param("slug", "Jwt-Shop")
                        .param("pickupAddress", "01 Võ Văn Ngân"))
                .andExpect(status().isForbidden());
        assertThat(shopCount()).isZero();

        mvc.perform(post("/user/shop/register").cookie(jwt).with(csrf())
                        .param("name", "JWT Shop")
                        .param("slug", "Jwt-Shop")
                        .param("description", "<script>alert(1)</script>")
                        .param("pickupAddress", "01 Võ Văn Ngân")
                        .param("ownerId", String.valueOf(spoofedOwner))
                        .param("status", "APPROVED")
                        .param("role", "VENDOR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/shop"));

        var row = jdbc.queryForMap("select * from uteexpress.shops where owner_id = ?", userId);
        assertThat(row.get("slug")).isEqualTo("jwt-shop");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("rejection_reason")).isNull();
        assertThat(row.get("logo_key")).isNull();
        assertThat(row.get("banner_key")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.shops where owner_id = ?", Integer.class, spoofedOwner))
                .isZero();
        assertThat(roleCodes(userId)).containsExactly("USER");
    }

    @Test
    void serviceAllowsDifferentOwnersButRejectsDuplicateOwnerAndCanonicalSlug() {
        long first = createUser("first", "USER");
        long second = createUser("second", "USER");
        authenticate(first, RoleCode.USER);
        shops.register(request("First Shop", "Shared-Slug"));

        assertThatThrownBy(() -> shops.register(request("Another Shop", "another-shop")))
                .isInstanceOf(ApplicationException.class);

        authenticate(second, RoleCode.USER);
        assertThatThrownBy(() -> shops.register(request("Second Shop", "shared-slug")))
                .isInstanceOf(ApplicationException.class);
        shops.register(request("Second Shop", "second-shop"));

        assertThat(shopCount()).isEqualTo(2);
        assertThat(roleCodes(first)).containsExactly("USER");
        assertThat(roleCodes(second)).containsExactly("USER");
    }

    @Test
    void concurrentDuplicateOwnerAndSlugAttemptsCreateAtMostOneRow() throws Exception {
        long sameOwner = createUser("same-owner", "USER");
        assertThat(runConcurrently(List.of(
                () -> registerAs(sameOwner, "Owner Shop A", "owner-a"),
                () -> registerAs(sameOwner, "Owner Shop B", "owner-b"))))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.shops where owner_id = ?", Integer.class, sameOwner))
                .isEqualTo(1);

        long slugOwner1 = createUser("slug-owner-1", "USER");
        long slugOwner2 = createUser("slug-owner-2", "USER");
        assertThat(runConcurrently(List.of(
                () -> registerAs(slugOwner1, "Slug Shop A", "same-slug"),
                () -> registerAs(slugOwner2, "Slug Shop B", "Same-Slug"))))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.shops where slug = 'same-slug'", Integer.class))
                .isEqualTo(1);
    }

    private int runConcurrently(List<Callable<Boolean>> attempts) throws Exception {
        CountDownLatch ready = new CountDownLatch(attempts.size());
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(attempts.size());
        try {
            List<Callable<Boolean>> synchronizedAttempts = new ArrayList<>();
            for (Callable<Boolean> attempt : attempts) {
                synchronizedAttempts.add(() -> {
                    ready.countDown();
                    start.await();
                    return attempt.call();
                });
            }
            var futures = synchronizedAttempts.stream().map(pool::submit).toList();
            ready.await();
            start.countDown();
            int successes = 0;
            for (var future : futures) {
                if (future.get()) successes++;
            }
            return successes;
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean registerAs(long userId, String name, String slug) {
        try {
            authenticate(userId, RoleCode.USER);
            shops.register(request(name, slug));
            return true;
        } catch (ApplicationException conflict) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Cookie login(String username) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                        .param("identifier", username)
                        .param("password", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn();
        return result.getResponse().getCookie("UTEEXPRESS_AUTH");
    }

    private long createUser(String username, String role) {
        String email = username + "@example.com";
        Long userId = jdbc.queryForObject("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash,
                     status, email_verified_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                returning id
                """, Long.class, email, email.toLowerCase(Locale.ROOT), username,
                username.toLowerCase(Locale.ROOT), passwordEncoder.encode(RAW_PASSWORD));
        jdbc.update("""
                insert into uteexpress.user_roles (user_id, role_id)
                select ?, id from uteexpress.roles where code = ?
                """, userId, role);
        return userId;
    }

    private void authenticate(long userId, RoleCode role) {
        var principal = new UteExpressPrincipal(userId, "shop-owner", null, 0,
                List.of(new SimpleGrantedAuthority(role.authority())), true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private void insertShop(long ownerId, String slug, String status) {
        jdbc.update("""
                insert into uteexpress.shops (owner_id, name, slug, pickup_address, status)
                values (?, 'Shop', ?, 'Pickup', ?)
                """, ownerId, slug, status);
    }

    private List<String> roleCodes(long userId) {
        return jdbc.queryForList("""
                select r.code from uteexpress.user_roles ur
                  join uteexpress.roles r on r.id = ur.role_id
                 where ur.user_id = ? order by r.code
                """, String.class, userId);
    }

    private int shopCount() {
        return jdbc.queryForObject("select count(*) from uteexpress.shops", Integer.class);
    }

    private static ShopRegistrationRequest request(String name, String slug) {
        return new ShopRegistrationRequest(name, slug, null, "Pickup");
    }
}
