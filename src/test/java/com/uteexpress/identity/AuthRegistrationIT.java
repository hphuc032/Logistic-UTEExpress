package com.uteexpress.identity;

import com.uteexpress.identity.dto.RegistrationCommand;
import com.uteexpress.identity.service.RegistrationConflictException;
import com.uteexpress.identity.service.RegistrationService;
import com.uteexpress.security.RoleCode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AuthRegistrationIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registrationService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void migrationCreatesIdentityTablesAndSeedsSecurityRoleContract() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("roles")).isTrue();
        assertThat(tableExists("user_roles")).isTrue();

        Set<String> seededRoles = Set.copyOf(jdbc.queryForList(
                "select code from uteexpress.roles", String.class));
        Set<String> securityRoles = Arrays.stream(RoleCode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertThat(seededRoles).containsExactlyInAnyOrderElementsOf(securityRoles).hasSize(5);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void registrationPersistsPendingUserWithBcryptAndOnlyDefaultUserRole() {
        String rawPassword = "RawSecret1";
        registrationService.register(new RegistrationCommand(
                "  First.User@Example.com ", "First.User", rawPassword));

        UserRow row = jdbc.queryForObject("""
                select email, normalized_email, username, normalized_username, password_hash,
                       status, email_verified_at is null, token_version, version
                  from uteexpress.users
                 where normalized_email = ?
                """, (rs, rowNum) -> new UserRow(
                rs.getString("email"), rs.getString("normalized_email"),
                rs.getString("username"), rs.getString("normalized_username"),
                rs.getString("password_hash"), rs.getString("status"),
                rs.getBoolean(7), rs.getLong("token_version"), rs.getLong("version")),
                "first.user@example.com");

        assertThat(row.email()).isEqualTo("First.User@Example.com");
        assertThat(row.normalizedEmail()).isEqualTo("first.user@example.com");
        assertThat(row.username()).isEqualTo("First.User");
        assertThat(row.normalizedUsername()).isEqualTo("first.user");
        assertThat(row.passwordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, row.passwordHash())).isTrue();
        assertThat(row.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(row.emailVerifiedAtNull()).isTrue();
        assertThat(row.tokenVersion()).isZero();
        assertThat(row.version()).isZero();
        assertThat(jdbc.queryForList("""
                select r.code
                  from uteexpress.user_roles ur
                  join uteexpress.roles r on r.id = ur.role_id
                  join uteexpress.users u on u.id = ur.user_id
                 where u.normalized_email = ?
                """, String.class, "first.user@example.com")).containsExactly("USER");
    }

    @Test
    void normalizedDuplicatesAreRejectedByServiceAndDatabaseConstraint() {
        registrationService.register(new RegistrationCommand(
                "Case@Test.com", "Case.Name", "RawSecret1"));

        assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                "  case@test.com ", "another-name", "RawSecret1")))
                .isInstanceOf(RegistrationConflictException.class)
                .hasMessageNotContaining("uq_users");
        assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                "another@test.com", "case.name", "RawSecret1")))
                .isInstanceOf(RegistrationConflictException.class)
                .hasMessageNotContaining("uq_users");

        assertThatThrownBy(() -> jdbc.update("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash, status)
                values (?, ?, ?, ?, ?, ?)
                """, "CASE@test.com", "case@test.com", "unique-name", "unique-name",
                "$2a$10$123456789012345678901u1234567890123456789012345678901",
                "PENDING_VERIFICATION"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash, status)
                values (?, ?, ?, ?, ?, ?)
                """, "unique@test.com", "unique@test.com", "CASE.NAME", "case.name",
                "$2a$10$123456789012345678901u1234567890123456789012345678901",
                "PENDING_VERIFICATION"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from information_schema.tables
                    where table_schema = 'uteexpress' and table_name = ?
                )
                """, Boolean.class, tableName));
    }

    private record UserRow(String email, String normalizedEmail, String username,
            String normalizedUsername, String passwordHash, String status,
            boolean emailVerifiedAtNull, long tokenVersion, long version) {
    }
}
