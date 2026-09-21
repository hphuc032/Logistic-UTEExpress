package com.uteexpress.identity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
