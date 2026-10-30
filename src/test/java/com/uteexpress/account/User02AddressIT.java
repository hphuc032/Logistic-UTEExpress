package com.uteexpress.account;

import com.uteexpress.account.dto.AddressData;
import com.uteexpress.account.dto.AddressForm;
import com.uteexpress.account.service.AddressQueryService;
import com.uteexpress.account.service.AddressService;
import com.uteexpress.security.RoleCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class User02AddressIT {
    private static final String PASSWORD = "AddressSecret1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AddressService addressService;
    @Autowired AddressQueryService addressQueries;

    @Test
    void migrationHibernateConstraintsAndPartialUniqueIndexAreValid() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(entityManagerFactory.isOpen()).isTrue();

        Integer tableCount = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = 'uteexpress' and table_name = 'addresses'
                """, Integer.class);
        assertThat(tableCount).isOne();
        assertThat(columnLength("receiver_name")).isEqualTo(120);
        assertThat(columnLength("phone")).isEqualTo(32);
        assertThat(columnLength("province_code")).isEqualTo(64);
        assertThat(columnLength("district")).isEqualTo(120);
        assertThat(columnLength("detail")).isEqualTo(255);

        long firstUser = createUser("schema-address-1");
        long secondUser = createUser("schema-address-2");
        insertAddress(firstUser, "First default", true);
        insertAddress(secondUser, "Independent default", true);
        assertThat(defaultCount(firstUser)).isOne();
        assertThat(defaultCount(secondUser)).isOne();

        assertThatThrownBy(() -> insertAddress(firstUser, "Second default", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        Object[] validAddress = {firstUser, "Receiver", "+84 90", "79", "Thu Duc", "Detail", false};
        for (int valueIndex = 1; valueIndex <= 5; valueIndex++) {
            Object[] invalidAddress = validAddress.clone();
            invalidAddress[valueIndex] = " ";
            assertThatThrownBy(() -> jdbc.update("""
                    insert into uteexpress.addresses
                        (user_id, receiver_name, phone, province_code, district, detail, is_default)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, invalidAddress)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertAddress(Long.MAX_VALUE, "Missing user", false))
                .isInstanceOf(DataIntegrityViolationException.class);

        String definition = jdbc.queryForObject("""
                select indexdef from pg_indexes
                 where schemaname = 'uteexpress' and indexname = 'uq_addresses_user_id_default'
                """, String.class);
        assertThat(definition).containsIgnoringCase("UNIQUE").containsIgnoringCase("WHERE (is_default = true)");
    }

    @Test
    void fullLifecycleKeepsExactlyOneDefaultAndIgnoresMassAssignment() throws Exception {
        long ownerId = createUser("address-owner");
        long otherId = createUser("address-other");
        Cookie jwt = login("address-owner");

        createAddress(jwt, "First receiver", "First detail", String.valueOf(otherId));
        createAddress(jwt, "Second receiver", "Second detail", String.valueOf(otherId));

        List<Long> ids = addressIds(ownerId);
        assertThat(ids).hasSize(2);
        assertThat(defaultCount(ownerId)).isOne();
        assertThat(defaultAddressId(ownerId)).isEqualTo(ids.getFirst());
        assertThat(addressCount(otherId)).isZero();

        mvc.perform(post("/user/addresses/{id}/update", ids.getFirst()).cookie(jwt).with(csrf())
                        .param("receiverName", "Updated receiver").param("phone", "+84 901")
                        .param("provinceCode", "79").param("district", "Thủ Đức")
                        .param("detail", "Updated detail").param("userId", String.valueOf(otherId))
                        .param("isDefault", "false").param("id", "999"))
                .andExpect(status().is3xxRedirection());
        assertThat(defaultAddressId(ownerId)).isEqualTo(ids.getFirst());

        mvc.perform(post("/user/addresses/{id}/default", ids.get(1)).cookie(jwt).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/addresses"));
        assertThat(defaultCount(ownerId)).isOne();
        assertThat(defaultAddressId(ownerId)).isEqualTo(ids.get(1));

        mvc.perform(post("/user/addresses/{id}/delete", ids.get(1)).cookie(jwt).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(addressCount(ownerId)).isOne();
        assertThat(defaultAddressId(ownerId)).isEqualTo(ids.getFirst());

        mvc.perform(post("/user/addresses/{id}/delete", ids.getFirst()).cookie(jwt).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(addressCount(ownerId)).isZero();
        assertThat(defaultCount(ownerId)).isZero();
    }

    @Test
    void realJwtOwnershipCsrfAndXssContractsHold() throws Exception {
        long firstUser = createUser("idor-first");
        createUser("idor-second");
        Cookie firstJwt = login("idor-first");
        Cookie secondJwt = login("idor-second");
        createAddress(firstJwt, "<script>alert(1)</script>", "<img src=x onerror=alert(1)>", null);
        long addressId = addressIds(firstUser).getFirst();

        mvc.perform(get("/user/addresses").cookie(firstJwt))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))));

        mvc.perform(post("/user/addresses/{id}/delete", addressId).cookie(firstJwt))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/addresses/{id}/update", addressId).cookie(secondJwt).with(csrf())
                        .param("receiverName", "Attack").param("phone", "+84 90")
                        .param("provinceCode", "79").param("district", "District")
                        .param("detail", "Attack detail"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/user/addresses/{id}/delete", addressId).cookie(secondJwt).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/user/addresses/{id}/default", addressId).cookie(secondJwt).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(addressCount(firstUser)).isOne();
        assertThat(jdbc.queryForObject(
                "select receiver_name from uteexpress.addresses where id = ?", String.class, addressId))
                .isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void checkoutReadContractReturnsTrustedOwnedAddressSnapshots() throws Exception {
        long userId = createUser("checkout-address");
        asUser(userId, () -> {
            addressService.create(form("Checkout default", "Default detail"));
            addressService.create(form("Checkout second", "Second detail"));
            return null;
        });

        assertThatThrownBy(() -> asRole(userId, RoleCode.ADMIN,
                () -> addressQueries.listOwnedAddresses(userId)))
                .isInstanceOf(AccessDeniedException.class);

        asUser(userId, () -> {
            List<AddressData> data = addressQueries.listOwnedAddresses(userId);
            assertThat(data).hasSize(2);
            assertThat(data.getFirst().defaultAddress()).isTrue();
            assertThat(addressQueries.requireOwnedAddress(userId, data.getFirst().id()))
                    .isEqualTo(data.getFirst());
            assertThat(addressQueries.findDefaultAddress(userId)).contains(data.getFirst());
            return null;
        });
    }

    @Test
    void concurrentFirstAddressCreationEndsWithTwoAddressesAndExactlyOneDefault() throws Exception {
        long userId = createUser("concurrent-create");
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> concurrentAsUser(userId, start,
                    () -> addressService.create(form("Concurrent A", "Detail A"))));
            var second = executor.submit(() -> concurrentAsUser(userId, start,
                    () -> addressService.create(form("Concurrent B", "Detail B"))));
            start.countDown();
            first.get();
            second.get();
        }
        assertThat(addressCount(userId)).isEqualTo(2);
        assertThat(defaultCount(userId)).isOne();
    }

    @Test
    void concurrentSetDefaultEndsWithExactlyOneDefault() throws Exception {
        long userId = createUser("concurrent-default");
        asUser(userId, () -> {
            addressService.create(form("A", "A detail"));
            addressService.create(form("B", "B detail"));
            addressService.create(form("C", "C detail"));
            return null;
        });
        List<Long> ids = addressIds(userId);

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> concurrentAsUser(userId, start,
                    () -> addressService.setDefault(ids.get(1))));
            var second = executor.submit(() -> concurrentAsUser(userId, start,
                    () -> addressService.setDefault(ids.get(2))));
            start.countDown();
            first.get();
            second.get();
        }
        assertThat(defaultCount(userId)).isOne();
        assertThat(defaultAddressId(userId)).isIn(ids.get(1), ids.get(2));
    }

    private void createAddress(Cookie jwt, String receiver, String detail, String forgedUserId) throws Exception {
        var request = post("/user/addresses").cookie(jwt).with(csrf())
                .param("receiverName", receiver).param("phone", "+84 900 000 000")
                .param("provinceCode", "79").param("district", "Thủ Đức")
                .param("detail", detail).param("isDefault", "true").param("id", "999");
        if (forgedUserId != null) request.param("userId", forgedUserId);
        mvc.perform(request).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/addresses"));
    }

    private Cookie login(String username) throws Exception {
        return mvc.perform(post("/login").with(csrf())
                        .param("identifier", username).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie()
                        .exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }

    private long createUser(String username) {
        String email = username + "@example.com";
        Long userId = jdbc.queryForObject("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash,
                     status, email_verified_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                returning id
                """, Long.class, email, email.toLowerCase(Locale.ROOT), username,
                username.toLowerCase(Locale.ROOT), passwordEncoder.encode(PASSWORD));
        jdbc.update("""
                insert into uteexpress.user_roles (user_id, role_id)
                select ?, id from uteexpress.roles where code = 'USER'
                """, userId);
        return userId;
    }

    private long insertAddress(long userId, String receiver, boolean defaultAddress) {
        return jdbc.queryForObject("""
                insert into uteexpress.addresses
                    (user_id, receiver_name, phone, province_code, district, detail, is_default)
                values (?, ?, '+84 90', '79', 'Thu Duc', 'Detail', ?)
                returning id
                """, Long.class, userId, receiver, defaultAddress);
    }

    private long addressCount(long userId) {
        return jdbc.queryForObject("select count(*) from uteexpress.addresses where user_id = ?",
                Long.class, userId);
    }

    private long defaultCount(long userId) {
        return jdbc.queryForObject("""
                select count(*) from uteexpress.addresses where user_id = ? and is_default = true
                """, Long.class, userId);
    }

    private Long defaultAddressId(long userId) {
        return jdbc.queryForObject("""
                select id from uteexpress.addresses where user_id = ? and is_default = true
                """, Long.class, userId);
    }

    private List<Long> addressIds(long userId) {
        return jdbc.queryForList(
                "select id from uteexpress.addresses where user_id = ? order by id", Long.class, userId);
    }

    private long columnLength(String column) {
        return jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                 where table_schema = 'uteexpress' and table_name = 'addresses' and column_name = ?
                """, Long.class, column);
    }

    private static AddressForm form(String receiver, String detail) {
        AddressForm form = new AddressForm();
        form.setReceiverName(receiver);
        form.setPhone("+84 900 000 000");
        form.setProvinceCode("79");
        form.setDistrict("Thủ Đức");
        form.setDetail(detail);
        return form;
    }

    private static <T> T concurrentAsUser(Long userId, CountDownLatch start, Callable<T> action)
            throws Exception {
        start.await();
        return asUser(userId, action);
    }

    private static <T> T asUser(Long userId, Callable<T> action) throws Exception {
        return asRole(userId, RoleCode.USER, action);
    }

    private static <T> T asRole(Long userId, RoleCode role, Callable<T> action) throws Exception {
        UteExpressPrincipal principal = new UteExpressPrincipal(userId, "user-" + userId, null, 0,
                List.of(new SimpleGrantedAuthority(role.authority())), true);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            return action.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
