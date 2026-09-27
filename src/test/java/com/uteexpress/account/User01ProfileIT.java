package com.uteexpress.account;

import com.uteexpress.support.TestImages;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class User01ProfileIT {
    private static final String OLD_PASSWORD = "OldSecret1";
    private static final String NEW_PASSWORD = "NewSecret2";
    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("uteexpress.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void migrationAndHibernateValidateProfileColumnsAndFlywayRerun() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(entityManagerFactory.isOpen()).isTrue();

        var columns = jdbc.queryForList("""
                select column_name, character_maximum_length, is_nullable
                  from information_schema.columns
                 where table_schema = 'uteexpress' and table_name = 'users'
                   and column_name in ('full_name', 'phone', 'avatar_key')
                 order by column_name
                """);
        assertThat(columns).hasSize(3);
        assertThat(length("full_name")).isEqualTo(120);
        assertThat(length("phone")).isEqualTo(32);
        assertThat(length("avatar_key")).isEqualTo(512);
        assertThat(columns).allSatisfy(column -> assertThat(column.get("is_nullable")).isEqualTo("YES"));
    }

    @Test
    void profileOwnershipAndMassAssignmentAreEnforcedByRealJwtAndDatabase() throws Exception {
        long firstId = createUser("owner01@example.com", "owner01", "USER");
        long secondId = createUser("other01@example.com", "other01", "USER");
        Cookie firstJwt = login("owner01", OLD_PASSWORD);

        mvc.perform(get("/user/profile").cookie(firstJwt))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("owner01@example.com")));

        mvc.perform(post("/user/profile").cookie(firstJwt).with(csrf())
                        .param("fullName", "  Nguyễn Văn A  ")
                        .param("phone", " +84 900-000-000 ")
                        .param("email", "attacker@example.com")
                        .param("username", "hacked")
                        .param("role", "ADMIN")
                        .param("status", "DISABLED")
                        .param("tokenVersion", "999")
                        .param("avatarKey", "../../escape")
                        .param("userId", String.valueOf(secondId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/profile"));

        var first = jdbc.queryForMap("select * from uteexpress.users where id = ?", firstId);
        var second = jdbc.queryForMap("select * from uteexpress.users where id = ?", secondId);
        assertThat(first.get("full_name")).isEqualTo("Nguyễn Văn A");
        assertThat(first.get("phone")).isEqualTo("+84 900-000-000");
        assertThat(first.get("email")).isEqualTo("owner01@example.com");
        assertThat(first.get("username")).isEqualTo("owner01");
        assertThat(first.get("status")).isEqualTo("ACTIVE");
        assertThat(first.get("token_version")).isEqualTo(0L);
        assertThat(first.get("avatar_key")).isNull();
        assertThat(second.get("full_name")).isNull();
        assertThat(roleCodes(firstId)).containsExactly("USER");
    }

    @Test
    void avatarIsValidatedStoredOutsideClasspathAndServedOnlyToItsOwner() throws Exception {
        long firstId = createUser("avatar01@example.com", "avatar01", "USER");
        createUser("avatar02@example.com", "avatar02", "USER");
        Cookie firstJwt = login("avatar01", OLD_PASSWORD);
        Cookie secondJwt = login("avatar02", OLD_PASSWORD);
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "../../client.png", "image/png", TestImages.png());

        mvc.perform(multipart("/user/avatar").file(avatar).cookie(firstJwt).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/profile"));

        String key = jdbc.queryForObject(
                "select avatar_key from uteexpress.users where id = ?", String.class, firstId);
        assertThat(key).matches("avatars/[0-9a-f-]{36}\\.png").doesNotContain("client");
        assertThat(Files.isRegularFile(STORAGE_ROOT.resolve(key))).isTrue();

        mvc.perform(get("/user/avatar").cookie(firstJwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(TestImages.png()));
        mvc.perform(get("/user/avatar").cookie(secondJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedPasswordChangeRevokesOldJwtAndChangesBcryptPasswordExactlyOnce() throws Exception {
        long userId = createUser("password01@example.com", "password01", "USER");
        Cookie oldJwt = login("password01", OLD_PASSWORD);

        mvc.perform(post("/user/password").cookie(oldJwt).with(csrf())
                        .param("currentPassword", "WrongSecret1")
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu hiện tại không chính xác")));
        assertThat(tokenVersion(userId)).isZero();

        mvc.perform(post("/user/password").cookie(oldJwt).with(csrf())
                        .param("currentPassword", OLD_PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordChanged=true"));

        assertThat(tokenVersion(userId)).isOne();
        String hash = passwordHash(userId);
        assertThat(hash).isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hash)).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();

        mvc.perform(get("/user/profile").cookie(oldJwt)).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", "password01").param("password", OLD_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không thể đăng nhập")))
                .andExpect(cookie().doesNotExist("UTEEXPRESS_AUTH"));
        Cookie newJwt = login("password01", NEW_PASSWORD);
        mvc.perform(get("/user/profile").cookie(newJwt)).andExpect(status().isOk());
    }

    @Test
    void unsafeMutationsStillRequireCsrfWithRealJwt() throws Exception {
        createUser("csrf01@example.com", "csrf01", "VENDOR");
        Cookie jwt = login("csrf01", OLD_PASSWORD);
        mvc.perform(post("/user/profile").cookie(jwt).param("fullName", "No CSRF"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/user/avatar").file(new MockMultipartFile(
                        "avatar", "a.png", "image/png", TestImages.png())).cookie(jwt))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/password").cookie(jwt)
                        .param("currentPassword", OLD_PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void nullableMigrationPreservesUsersCreatedBeforeUser01() {
        try (PostgreSQLContainer upgradeDb = new PostgreSQLContainer("postgres:17.6")) {
            upgradeDb.start();
            Flyway beforeUser01 = Flyway.configure()
                    .dataSource(upgradeDb.getJdbcUrl(), upgradeDb.getUsername(), upgradeDb.getPassword())
                    .schemas("uteexpress").defaultSchema("uteexpress")
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("20260926035526"))
                    .load();
            beforeUser01.migrate();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(new DriverManagerDataSource(
                    upgradeDb.getJdbcUrl(), upgradeDb.getUsername(), upgradeDb.getPassword()));
            Long userId = upgradeJdbc.queryForObject("""
                    insert into uteexpress.users
                        (email, normalized_email, username, normalized_username, password_hash, status)
                    values ('before@example.com', 'before@example.com', 'before01', 'before01', ?, 'ACTIVE')
                    returning id
                    """, Long.class, passwordEncoder.encode(OLD_PASSWORD));

            Flyway latest = Flyway.configure()
                    .dataSource(upgradeDb.getJdbcUrl(), upgradeDb.getUsername(), upgradeDb.getPassword())
                    .schemas("uteexpress").defaultSchema("uteexpress")
                    .locations("classpath:db/migration")
                    .load();
            assertThat(latest.migrate().migrationsExecuted).isOne();
            var row = upgradeJdbc.queryForMap("select * from uteexpress.users where id = ?", userId);
            assertThat(row.get("email")).isEqualTo("before@example.com");
            assertThat(row.get("full_name")).isNull();
            assertThat(row.get("phone")).isNull();
            assertThat(row.get("avatar_key")).isNull();
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }

    private Cookie login(String identifier, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf())
                        .param("identifier", identifier).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }

    private long createUser(String email, String username, String role) {
        Long userId = jdbc.queryForObject("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash,
                     status, email_verified_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                returning id
                """, Long.class, email, email.toLowerCase(Locale.ROOT), username,
                username.toLowerCase(Locale.ROOT), passwordEncoder.encode(OLD_PASSWORD));
        jdbc.update("""
                insert into uteexpress.user_roles (user_id, role_id)
                select ?, id from uteexpress.roles where code = ?
                """, userId, role);
        return userId;
    }

    private long length(String column) {
        return jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                 where table_schema = 'uteexpress' and table_name = 'users' and column_name = ?
                """, Long.class, column);
    }

    private java.util.List<String> roleCodes(long userId) {
        return jdbc.queryForList("""
                select r.code from uteexpress.user_roles ur
                join uteexpress.roles r on r.id = ur.role_id
                where ur.user_id = ? order by r.code
                """, String.class, userId);
    }

    private long tokenVersion(long userId) {
        return jdbc.queryForObject(
                "select token_version from uteexpress.users where id = ?", Long.class, userId);
    }

    private String passwordHash(long userId) {
        return jdbc.queryForObject(
                "select password_hash from uteexpress.users where id = ?", String.class, userId);
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("uteexpress-user01-" + UUID.randomUUID());
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
