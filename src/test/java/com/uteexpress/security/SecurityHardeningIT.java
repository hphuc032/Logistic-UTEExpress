package com.uteexpress.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises persisted governance mutations through the real JWT filter, without mock principals. */
@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class SecurityHardeningIT {
    private static final String PASSWORD = "SecurityGate1";
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Test
    void adminLockAndUnlockRevokePreviouslyIssuedJwtAndUsePersistedAuditActor() throws Exception {
        Account admin = account("ADMIN");
        Account buyer = account("USER");
        Cookie adminJwt = login(admin);
        Cookie buyerJwt = login(buyer);
        String path = "/admin/accounts/" + buyer.id();
        mvc.perform(get("/user/profile").cookie(buyerJwt)).andExpect(status().isOk());
        mvc.perform(post(path + "/lock").cookie(adminJwt).with(csrf())
                        .param("version", version(buyer)).param("actorId", buyer.id().toString())
                        .param("tokenVersion", "999").param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection());
        assertThat(tokenVersion(buyer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from uteexpress.users where id=?", String.class, buyer.id()))
                .isEqualTo("LOCKED");
        mvc.perform(get("/user/profile").cookie(buyerJwt)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select actor_id from uteexpress.audit_logs where target_id=? and action='ACCOUNT_LOCKED'",
                Long.class, buyer.id())).isEqualTo(admin.id());
        mvc.perform(post(path + "/unlock").cookie(adminJwt).with(csrf()).param("version", version(buyer)))
                .andExpect(status().is3xxRedirection());
        assertThat(tokenVersion(buyer)).isEqualTo(2);
        mvc.perform(get("/user/profile").cookie(buyerJwt)).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/profile").cookie(login(buyer))).andExpect(status().isOk());
    }

    @Test
    void adminRoleGrantAndRevokeInvalidateOldJwtRatherThanGrantingStaleCookieNewPrivileges() throws Exception {
        Account admin = account("ADMIN");
        Account buyer = account("USER");
        Cookie adminJwt = login(admin);
        Cookie originalJwt = login(buyer);
        String path = "/admin/accounts/" + buyer.id() + "/roles/MANAGER/";
        mvc.perform(post(path + "grant").cookie(adminJwt).with(csrf()).param("version", version(buyer)))
                .andExpect(status().is3xxRedirection());
        assertThat(tokenVersion(buyer)).isEqualTo(1);
        mvc.perform(get("/manager/dashboard").cookie(originalJwt)).andExpect(status().isUnauthorized());
        Cookie grantedJwt = login(buyer);
        mvc.perform(get("/manager/dashboard").cookie(grantedJwt)).andExpect(status().isOk());
        mvc.perform(post(path + "revoke").cookie(adminJwt).with(csrf()).param("version", version(buyer)))
                .andExpect(status().is3xxRedirection());
        assertThat(tokenVersion(buyer)).isEqualTo(2);
        mvc.perform(get("/manager/dashboard").cookie(grantedJwt)).andExpect(status().isUnauthorized());
        mvc.perform(get("/manager/dashboard").cookie(login(buyer))).andExpect(status().isForbidden());
    }

    @Test
    void managerCannotMutateAdminResourcesAndAdminMutationRequiresCsrf() throws Exception {
        Account admin = account("ADMIN");
        Account manager = account("MANAGER");
        Account buyer = account("USER");
        Cookie adminJwt = login(admin);
        Cookie managerJwt = login(manager);
        String path = "/admin/accounts/" + buyer.id();
        mvc.perform(post(path + "/lock").cookie(adminJwt).param("version", version(buyer)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/lock").cookie(managerJwt).with(csrf()).param("version", version(buyer)))
                .andExpect(status().isForbidden());
        // Also prove the service guard on the Manager route that passes the URL role matcher.
        mvc.perform(post("/manager/accounts/" + buyer.id() + "/lock").cookie(managerJwt).with(csrf())
                        .param("version", version(buyer)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/roles/ADMIN/grant").cookie(managerJwt).with(csrf()).param("version", version(buyer)))
                .andExpect(status().isForbidden());
        assertThat(tokenVersion(buyer)).isZero();
        assertThat(jdbc.queryForObject("select status from uteexpress.users where id=?", String.class, buyer.id()))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.audit_logs where target_type='ACCOUNT' and target_id=?",
                Integer.class, buyer.id())).isZero();
    }

    private Account account(String role) {
        String name = "sec02-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Long id = jdbc.queryForObject("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash, status, email_verified_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP) returning id
                """, Long.class, name + "@example.test", name + "@example.test", name, name, encoder.encode(PASSWORD));
        jdbc.update("insert into uteexpress.user_roles(user_id,role_id) select ?,id from uteexpress.roles where code=?", id, role);
        return new Account(id, name);
    }

    private Cookie login(Account account) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("identifier", account.username()).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }

    private String version(Account account) {
        return jdbc.queryForObject("select version from uteexpress.users where id=?", Long.class, account.id()).toString();
    }

    private long tokenVersion(Account account) {
        return jdbc.queryForObject("select token_version from uteexpress.users where id=?", Long.class, account.id());
    }

    private record Account(Long id, String username) { }
}
