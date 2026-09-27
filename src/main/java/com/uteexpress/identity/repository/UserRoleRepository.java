package com.uteexpress.identity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class UserRoleRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void assign(Long userId, Long roleId) {
        jdbcTemplate.update(
                "insert into uteexpress.user_roles (user_id, role_id) values (?, ?)",
                userId, roleId);
    }

    public void assignVendorIfAbsent(Long userId) {
        jdbcTemplate.update("""
                insert into uteexpress.user_roles (user_id, role_id)
                select ?, r.id from uteexpress.roles r where r.code = 'VENDOR'
                on conflict (user_id, role_id) do nothing
                """, userId);
    }

    public Set<String> findRoleCodes(Long userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                select r.code
                  from uteexpress.user_roles ur
                  join uteexpress.roles r on r.id = ur.role_id
                 where ur.user_id = ?
                 order by r.code
                """, String.class, userId));
    }
}
