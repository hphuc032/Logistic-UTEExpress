package com.uteexpress.security;

import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(Auth02AuthenticationIT.AuthFixtureConfiguration.class)
class Auth02AuthenticationIT {
    private static final String RAW_PASSWORD = "RawSecret1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdentityAuthenticationService identities;

    @Test
    void activeAccountCanLoginByCaseInsensitiveUsernameOrEmail() throws Exception {
        createUser("Login.User@Example.com", "Login.User", "ACTIVE", "USER");

        Cookie usernameCookie = login("  LOGIN.USER ", RAW_PASSWORD, "/");
        Cookie emailCookie = login(" login.user@EXAMPLE.COM ", RAW_PASSWORD, "/");

        assertThat(usernameCookie.getValue()).isNotBlank();
        assertThat(emailCookie.getValue()).isNotBlank();
        assertThat(usernameCookie.isHttpOnly()).isTrue();
    }

    @Test
    void pendingLockedAndDisabledAccountsReceiveSameGenericFailure() throws Exception {
        createUser("pending@example.com", "pending-user", "PENDING_VERIFICATION", "USER");
        createUser("locked@example.com", "locked-user", "LOCKED", "USER");
        createUser("disabled@example.com", "disabled-user", "DISABLED", "USER");

        for (String identifier : new String[]{"pending-user", "locked-user", "disabled-user"}) {
            mvc.perform(post("/login").with(csrf())
                            .param("identifier", identifier)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "Không thể đăng nhập với thông tin đã cung cấp.")))
                    .andExpect(cookie().doesNotExist("UTEEXPRESS_AUTH"));
        }
    }

    @Test
    void logoutAtomicallyInvalidatesOldJwtAndTokenVersionNeverDecreases() throws Exception {
        long userId = createUser("logout@example.com", "logout-user", "ACTIVE", "USER");
        Cookie jwt = login("logout-user", RAW_PASSWORD, "/");

        mvc.perform(get("/test-fixtures/auth/account").cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(userId)));
        long before = tokenVersion(userId);

        mvc.perform(post("/logout").with(csrf()).cookie(jwt))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout=true"))
                .andExpect(cookie().maxAge("UTEEXPRESS_AUTH", 0));

        long afterLogout = tokenVersion(userId);
        assertThat(afterLogout).isGreaterThan(before);
        mvc.perform(get("/test-fixtures/auth/account").cookie(jwt))
                .andExpect(status().isUnauthorized());

        assertThat(identities.invalidateTokens(userId)).isTrue();
        assertThat(tokenVersion(userId)).isGreaterThan(afterLogout);
    }

    @Test
    void sameJwtUsesCurrentDatabaseRoleAndAdminLoginAuthorizesAdminRoute() throws Exception {
        long userId = createUser("roles@example.com", "role-user", "ACTIVE", "USER");
        Cookie userJwt = login("role-user", RAW_PASSWORD, "/");

        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/dashboard").cookie(userJwt))
                .andExpect(status().isForbidden());

        replaceRole(userId, "ADMIN");
        mvc.perform(get("/admin/dashboard").cookie(userJwt))
                .andExpect(status().isOk());

        createUser("admin@example.com", "admin-user", "ACTIVE", "ADMIN");
        Cookie adminJwt = login("admin@example.com", RAW_PASSWORD, "/admin/dashboard");
        mvc.perform(get("/admin/dashboard").cookie(adminJwt))
                .andExpect(status().isOk());
        mvc.perform(get("/manager/dashboard").cookie(adminJwt))
                .andExpect(status().isForbidden());

        createUser("manager@example.com", "manager-user", "ACTIVE", "MANAGER");
        Cookie managerJwt = login("manager-user", RAW_PASSWORD, "/manager/dashboard");
        mvc.perform(get("/manager/dashboard").cookie(managerJwt))
                .andExpect(status().isOk());
    }

    @Test
    void lockingOrDisablingAccountImmediatelyRejectsPreviouslyIssuedJwt() throws Exception {
        long lockedUserId = createUser("lock-after@example.com", "lock-after", "ACTIVE", "USER");
        Cookie lockedJwt = login("lock-after", RAW_PASSWORD, "/");
        jdbc.update("update uteexpress.users set status = 'LOCKED' where id = ?", lockedUserId);
        mvc.perform(get("/test-fixtures/auth/account").cookie(lockedJwt))
                .andExpect(status().isUnauthorized());

        long disabledUserId = createUser(
                "disable-after@example.com", "disable-after", "ACTIVE", "USER");
        Cookie disabledJwt = login("disable-after", RAW_PASSWORD, "/");
        jdbc.update("update uteexpress.users set status = 'DISABLED' where id = ?", disabledUserId);
        mvc.perform(get("/test-fixtures/auth/account").cookie(disabledJwt))
                .andExpect(status().isUnauthorized());
    }

    private Cookie login(String identifier, String password, String redirect) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                        .param("identifier", identifier)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(redirect))
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn();
        return result.getResponse().getCookie("UTEEXPRESS_AUTH");
    }

    private long createUser(String email, String username, String status, String role) {
        Long userId = jdbc.queryForObject("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash,
                     status, email_verified_at)
                values (?, ?, ?, ?, ?, ?, case when ? = 'ACTIVE' then CURRENT_TIMESTAMP else null end)
                returning id
                """, Long.class,
                email, email.trim().toLowerCase(Locale.ROOT),
                username, username.trim().toLowerCase(Locale.ROOT),
                passwordEncoder.encode(RAW_PASSWORD), status, status);
        jdbc.update("""
                insert into uteexpress.user_roles (user_id, role_id)
                select ?, id from uteexpress.roles where code = ?
                """, userId, role);
        return userId;
    }

    private void replaceRole(long userId, String role) {
        jdbc.update("delete from uteexpress.user_roles where user_id = ?", userId);
        jdbc.update("""
                insert into uteexpress.user_roles (user_id, role_id)
                select ?, id from uteexpress.roles where code = ?
                """, userId, role);
    }

    private long tokenVersion(long userId) {
        return jdbc.queryForObject(
                "select token_version from uteexpress.users where id = ?", Long.class, userId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthFixtureConfiguration {
        @Bean
        AuthFixtureController authFixtureController(CurrentAccountIdProvider accountIds) {
            return new AuthFixtureController(accountIds);
        }
    }

    @RestController
    static class AuthFixtureController {
        private final CurrentAccountIdProvider accountIds;

        AuthFixtureController(CurrentAccountIdProvider accountIds) {
            this.accountIds = accountIds;
        }

        @GetMapping("/test-fixtures/auth/account")
        String accountId() {
            return String.valueOf(accountIds.requireCurrentAccountId());
        }
    }
}
