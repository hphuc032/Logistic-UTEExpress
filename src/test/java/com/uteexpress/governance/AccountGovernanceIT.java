package com.uteexpress.governance;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.governance.service.AccountGovernanceService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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

@SpringBootTest @Testcontainers
class AccountGovernanceIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired AccountGovernanceService accounts;
    @Autowired JdbcTemplate jdbc;
    Long admin;
    Long buyer;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM uteexpress.audit_logs");
        admin = insert("admin", "ACTIVE");
        buyer = insert("buyer", "ACTIVE");
        authenticate(admin, "ADMIN");
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void lockAndUnlockRevokeTokensAndWriteAudit() {
        var found = accounts.search("buyer", 0);
        assertThat(found.getTotalElements()).isGreaterThanOrEqualTo(1);
        var before = accounts.get(buyer);
        accounts.setLocked(buyer, before.version(), true);
        var locked = accounts.get(buyer);
        assertThat(locked.status()).isEqualTo("LOCKED");
        assertThat(tokenVersion(buyer)).isEqualTo(1);
        accounts.setLocked(buyer, locked.version(), false);
        assertThat(accounts.get(buyer).status()).isEqualTo("ACTIVE");
        assertThat(tokenVersion(buyer)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_id=? AND target_type='ACCOUNT'", Integer.class, buyer)).isEqualTo(2);
    }

    @Test void staleVersionSelfLockAndWrongRoleDoNotChangeAccount() {
        var before = accounts.get(buyer);
        assertThatThrownBy(() -> accounts.setLocked(admin, accounts.get(admin).version(), true))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> accounts.setLocked(buyer, before.version() + 1, true))
                .isInstanceOf(ApplicationException.class);
        authenticate(admin, "MANAGER");
        assertThat(accounts.get(buyer).email()).contains("buyer");
        assertThatThrownBy(() -> accounts.setLocked(buyer, before.version(), true))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(accounts.get(buyer).status()).isEqualTo("ACTIVE");
        assertThat(tokenVersion(buyer)).isZero();
    }

    @Test void auditFailureRollsBackLockAndTokenInvalidation() {
        var before = accounts.get(buyer);
        authenticate(Long.MAX_VALUE, "ADMIN");
        assertThatThrownBy(() -> accounts.setLocked(buyer, before.version(), true))
                .isInstanceOf(DataIntegrityViolationException.class);
        authenticate(admin, "ADMIN");
        assertThat(accounts.get(buyer).status()).isEqualTo("ACTIVE");
        assertThat(tokenVersion(buyer)).isZero();
    }

    private Long insert(String name, String status) {
        String unique = name + System.nanoTime();
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash, status)
                VALUES (?, ?, ?, ?, 'not-a-login-password', ?)
                RETURNING id
                """, Long.class, unique + "@example.test", unique + "@example.test", unique, unique, status);
    }

    private long tokenVersion(Long id) {
        return jdbc.queryForObject("SELECT token_version FROM uteexpress.users WHERE id=?", Long.class, id);
    }

    private void authenticate(Long id, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var principal = new UteExpressPrincipal(id, "admin", null, 0, authorities, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }
}
