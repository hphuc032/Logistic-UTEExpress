package com.uteexpress.governance;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.governance.service.AccountGovernanceService;
import com.uteexpress.governance.service.RoleGovernanceService;
import com.uteexpress.governance.dto.ManagedRole;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class RoleGovernanceIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired RoleGovernanceService roles;
    @Autowired AccountGovernanceService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    Long admin;
    Long buyer;

    @BeforeEach void setup() {
        admin = insert("admin", "ACTIVE");
        buyer = insert("buyer", "ACTIVE");
        jdbc.update("""
                INSERT INTO uteexpress.user_roles (user_id, role_id)
                SELECT ?, id FROM uteexpress.roles WHERE code = 'ADMIN'
                """, admin);
        authenticate(admin, "ADMIN");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void grantAndRevokeShipperRotateTokenAndUpdateActiveList() {
        long version = accounts.get(buyer).version();
        roles.change(buyer, version, ManagedRole.SHIPPER, true);
        assertThat(roles.rolesFor(buyer)).contains("SHIPPER");
        assertThat(roles.activeShippers()).extracting("id").contains(buyer);
        assertThat(tokenVersion(buyer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM uteexpress.audit_logs
                WHERE target_type='ACCOUNT' AND target_id=? AND action='ROLE_GRANTED'
                """, Integer.class, buyer)).isEqualTo(1);
        assertThatThrownBy(() -> roles.change(buyer, version, ManagedRole.MANAGER, true))
                .isInstanceOf(ApplicationException.class);
        roles.change(buyer, accounts.get(buyer).version(), ManagedRole.SHIPPER, false);
        assertThat(roles.activeShippers()).extracting("id").doesNotContain(buyer);
        assertThat(tokenVersion(buyer)).isEqualTo(2);
    }

    @Test void lastActiveAdminCannotBeRevokedOrLocked() {
        // The project seed has an admin; isolate this test's sole-admin invariant.
        jdbc.update("""
                DELETE FROM uteexpress.user_roles
                WHERE role_id = (SELECT id FROM uteexpress.roles WHERE code = 'ADMIN') AND user_id <> ?
                """, admin);
        assertThatThrownBy(() -> roles.change(admin, accounts.get(admin).version(), ManagedRole.ADMIN, false))
                .isInstanceOf(ApplicationException.class);
        authenticate(buyer, "ADMIN");
        assertThatThrownBy(() -> accounts.setLocked(admin, accounts.get(admin).version(), true))
                .isInstanceOf(ApplicationException.class);
        assertThat(roles.rolesFor(admin)).contains("ADMIN");
        assertThat(accounts.get(admin).status()).isEqualTo("ACTIVE");
    }

    @Test void restrictedRolesAndManagerWriteAreDenied() {
        long version = accounts.get(buyer).version();
        assertThatThrownBy(() -> ManagedRole.valueOf("USER")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ManagedRole.valueOf("VENDOR")).isInstanceOf(IllegalArgumentException.class);
        authenticate(admin, "MANAGER");
        assertThatThrownBy(() -> roles.change(buyer, version, ManagedRole.SHIPPER, true))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(roles.rolesFor(buyer)).doesNotContain("SHIPPER");
    }

    @Test void auditFailureRollsBackRoleAndToken() {
        authenticate(Long.MAX_VALUE, "ADMIN");
        long version = accounts.get(buyer).version();
        assertThatThrownBy(() -> roles.change(buyer, version, ManagedRole.MANAGER, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(roles.rolesFor(buyer)).doesNotContain("MANAGER");
        assertThat(tokenVersion(buyer)).isZero();
    }

    @Test void roleActionsRequireAdminAndCsrfAndRenderUpdatedAccount() throws Exception {
        String path = "/admin/accounts/" + buyer + "/roles/SHIPPER/grant";
        String version = String.valueOf(accounts.get(buyer).version());
        mvc.perform(post(path).with(user(principal(admin, "ADMIN"))).param("version", version))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(principal(admin, "MANAGER"))).with(csrf())
                        .param("version", version)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(principal(admin, "ADMIN"))).with(csrf())
                        .param("version", version)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/accounts/" + buyer).with(user(principal(admin, "ADMIN"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("SHIPPER")));
        mvc.perform(get("/manager/shippers").with(user(principal(admin, "MANAGER"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Shipper đang hoạt động")));
    }

    private Long insert(String prefix, String status) {
        String unique = prefix + System.nanoTime();
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash, status)
                VALUES (?, ?, ?, ?, 'test-only-password', ?) RETURNING id
                """, Long.class, unique + "@example.test", unique + "@example.test", unique, unique, status);
    }

    private long tokenVersion(long accountId) {
        return jdbc.queryForObject("SELECT token_version FROM uteexpress.users WHERE id=?", Long.class, accountId);
    }

    private void authenticate(Long accountId, String role) {
        var principal = principal(accountId, role);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private UteExpressPrincipal principal(Long accountId, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new UteExpressPrincipal(accountId, "ops", null, 0, authorities, true);
    }
}
