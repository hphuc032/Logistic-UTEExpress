package com.uteexpress.governance;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.service.CommissionPolicyManagementService;
import com.uteexpress.governance.service.CommissionQueryService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class CommissionPolicyIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired CommissionQueryService queries;
    @Autowired CommissionPolicyManagementService management;
    @Autowired MockMvc mvc;
    Long admin;

    @BeforeEach void setup() {
        String name = "commission" + System.nanoTime();
        admin = jdbc.queryForObject("""
                INSERT INTO uteexpress.users (email,normalized_email,username,normalized_username,password_hash,status)
                VALUES (?, ?, ?, ?, 'not-a-password', 'ACTIVE') RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name);
        authenticate("ADMIN");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void missingPolicyFailsClosedAndNewVersionBecomesEffectiveAtItsStart() {
        Instant first = Instant.parse("2031-01-01T00:00:00Z");
        Instant next = Instant.parse("2031-02-01T00:00:00Z");
        assertThatThrownBy(() -> queries.requireEffectivePolicy(first))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        Long firstId = management.create(new BigDecimal("7.5000"), first);
        Long nextId = management.create(new BigDecimal("8.2500"), next);
        assertThat(queries.requireEffectivePolicy(next.minusNanos(1)).policyId()).isEqualTo(firstId);
        var snapshot = queries.requireEffectivePolicy(next);
        assertThat(snapshot.policyId()).isEqualTo(nextId);
        assertThat(snapshot.ratePercent()).isEqualByComparingTo("8.2500");
        assertThat(snapshot.effectiveFrom()).isEqualTo(next);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='COMMISSION_POLICY' AND target_id=?",
                Integer.class, nextId)).isEqualTo(1);
    }

    @Test void duplicateTimeInvalidRateAndManagerWriteAreRejected() {
        Instant time = Instant.parse("2032-01-01T00:00:00Z");
        management.create(new BigDecimal("10"), time);
        assertThatThrownBy(() -> management.create(new BigDecimal("12"), time))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> management.create(new BigDecimal("100.0001"), time.plusSeconds(1)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        authenticate("MANAGER");
        assertThat(management.list()).isNotEmpty();
        assertThatThrownBy(() -> management.create(BigDecimal.ONE, time.plusSeconds(2)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void adminUiRequiresCsrfAndOtherRolesCannotCreate() throws Exception {
        var adminPrincipal = principal("ADMIN");
        mvc.perform(post("/admin/commissions").with(user(adminPrincipal))
                        .param("ratePercent", "9").param("effectiveFrom", "2033-01-01T00:00:00Z"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/commissions").with(user(adminPrincipal)).with(csrf())
                        .param("ratePercent", "9").param("effectiveFrom", "2033-01-01T00:00:00Z"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/manager/commissions").with(user(principal("MANAGER"))))
                .andExpect(status().isOk());
        mvc.perform(post("/manager/commissions").with(user(principal("MANAGER"))).with(csrf())
                        .param("ratePercent", "9").param("effectiveFrom", "2033-02-01T00:00:00Z"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/commissions").with(user(principal("USER"))))
                .andExpect(status().isForbidden());
    }

    private UteExpressPrincipal principal(String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new UteExpressPrincipal(admin, "commission", null, 0, authorities, true);
    }

    private void authenticate(String role) {
        var user = principal(role);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }
}
