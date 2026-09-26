package com.uteexpress.governance;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.shop.service.ShopApprovalService;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ShopApprovalIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired ShopApprovalService approvals;
    @Autowired JdbcTemplate jdbc;
    Long adminId;
    Long ownerId;
    Long shopId;

    @BeforeEach void setup() {
        jdbc.update("delete from uteexpress.audit_logs");
        jdbc.update("delete from uteexpress.shops");
        jdbc.update("delete from uteexpress.user_roles");
        jdbc.update("delete from uteexpress.users");
        adminId = createUser("admin", "ACTIVE");
        ownerId = createUser("owner", "ACTIVE");
        shopId = jdbc.queryForObject("""
                insert into uteexpress.shops (owner_id, name, slug, pickup_address, status)
                values (?, 'Test Shop', 'test-shop', 'Pickup', 'PENDING') returning id
                """, Long.class, ownerId);
        authenticate(adminId, "ADMIN");
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void approvalGrantsVendorAndInvalidatesOldJwtWithAudit() {
        var approved = approvals.approve(shopId, 0L);
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.version()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                select count(*) from uteexpress.user_roles ur
                join uteexpress.roles r on r.id = ur.role_id
                where ur.user_id = ? and r.code = 'VENDOR'
                """, Integer.class, ownerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select token_version from uteexpress.users where id = ?",
                Long.class, ownerId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.audit_logs where action = 'SHOP_APPROVED' and actor_id = ? and target_id = ?",
                Integer.class, adminId, shopId)).isEqualTo(1);
        assertThatThrownBy(() -> approvals.approve(shopId, 0L))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test void rejectionStoresTrimmedReasonWithoutGrantingRole() {
        var rejected = approvals.reject(shopId, 0L, "  Thiếu địa chỉ hợp lệ  ");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.rejectionReason()).isEqualTo("Thiếu địa chỉ hợp lệ");
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.user_roles where user_id = ?",
                Integer.class, ownerId)).isZero();
        assertThat(jdbc.queryForObject("select token_version from uteexpress.users where id = ?",
                Long.class, ownerId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.audit_logs where action = 'SHOP_REJECTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test void staleVersionAndInvalidReasonLeaveDatabaseUntouched() {
        assertThatThrownBy(() -> approvals.approve(shopId, 99L)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> approvals.reject(shopId, 0L, " ")).isInstanceOf(ApplicationException.class);
        assertThat(jdbc.queryForObject("select status from uteexpress.shops where id = ?",
                String.class, shopId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.audit_logs", Integer.class)).isZero();
    }

    @Test void inactiveOwnerCannotBeApproved() {
        jdbc.update("update uteexpress.users set status = 'LOCKED' where id = ?", ownerId);
        assertThatThrownBy(() -> approvals.approve(shopId, 0L)).isInstanceOf(ApplicationException.class);
        assertThat(jdbc.queryForObject("select status from uteexpress.shops where id = ?",
                String.class, shopId)).isEqualTo("PENDING");
    }

    @Test void nonAdminCannotUseService() {
        authenticate(adminId, "MANAGER");
        assertThatThrownBy(() -> approvals.approve(shopId, 0L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvals.pending(0)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void auditFailureRollsBackShopRoleAndTokenTogether() {
        authenticate(Long.MAX_VALUE, "ADMIN");
        assertThatThrownBy(() -> approvals.approve(shopId, 0L)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select status from uteexpress.shops where id = ?",
                String.class, shopId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.user_roles where user_id = ?",
                Integer.class, ownerId)).isZero();
        assertThat(jdbc.queryForObject("select token_version from uteexpress.users where id = ?",
                Long.class, ownerId)).isZero();
    }

    private Long createUser(String prefix, String status) {
        return jdbc.queryForObject("""
                insert into uteexpress.users
                (email, normalized_email, username, normalized_username, password_hash, status)
                values (?, ?, ?, ?, 'not-a-login-password', ?) returning id
                """, Long.class, prefix + "@example.test", prefix + "@example.test", prefix, prefix, status);
    }

    private void authenticate(Long id, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var principal = new UteExpressPrincipal(id, "reviewer", null, 0, authorities, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }
}
