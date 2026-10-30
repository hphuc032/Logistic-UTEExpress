package com.uteexpress.governance.repository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

@Repository
public class RoleGovernanceRepository {
    public record AccountState(long version, String status) { }
    public record ActiveShipper(long id, String username, String fullName) { }

    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    public RoleGovernanceRepository(ObjectProvider<JdbcTemplate> jdbcProvider) { this.jdbcProvider = jdbcProvider; }

    private JdbcTemplate jdbc() { return jdbcProvider.getObject(); }

    /** Serializes ADMIN grants, revocations and account locks for the last-admin invariant. */
    public long lockRole(String code) {
        return jdbc().queryForObject("SELECT id FROM uteexpress.roles WHERE code = ? FOR UPDATE", Long.class, code);
    }

    public AccountState lockAccount(long id) {
        List<AccountState> rows = jdbc().query("SELECT version, status FROM uteexpress.users WHERE id = ? FOR UPDATE",
                (row, ignored) -> new AccountState(row.getLong(1), row.getString(2)), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Set<String> rolesFor(long accountId) {
        return new LinkedHashSet<>(jdbc().queryForList("""
                SELECT r.code FROM uteexpress.user_roles ur
                JOIN uteexpress.roles r ON r.id = ur.role_id
                WHERE ur.user_id = ? ORDER BY r.code
                """, String.class, accountId));
    }

    public long activeAdminCount() {
        return jdbc().queryForObject("""
                SELECT count(*) FROM uteexpress.users u
                JOIN uteexpress.user_roles ur ON ur.user_id = u.id
                JOIN uteexpress.roles r ON r.id = ur.role_id
                WHERE u.status = 'ACTIVE' AND r.code = 'ADMIN'
                """, Long.class);
    }

    public int assign(long accountId, long roleId) {
        return jdbc().update("INSERT INTO uteexpress.user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                accountId, roleId);
    }

    public int revoke(long accountId, long roleId) {
        return jdbc().update("DELETE FROM uteexpress.user_roles WHERE user_id = ? AND role_id = ?", accountId, roleId);
    }

    public int rotateToken(long accountId, long expectedVersion) {
        return jdbc().update("""
                UPDATE uteexpress.users SET token_version = token_version + 1,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version = ?
                """, accountId, expectedVersion);
    }

    public List<ActiveShipper> activeShippers() {
        return jdbc().query("""
                SELECT u.id, u.username, u.full_name FROM uteexpress.users u
                JOIN uteexpress.user_roles ur ON ur.user_id = u.id
                JOIN uteexpress.roles r ON r.id = ur.role_id
                WHERE u.status = 'ACTIVE' AND r.code = 'SHIPPER'
                ORDER BY u.id
                """, (row, ignored) -> new ActiveShipper(row.getLong(1), row.getString(2), row.getString(3)));
    }
}
