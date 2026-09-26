package com.uteexpress.identity;

import com.uteexpress.identity.entity.OtpPurpose;
import com.uteexpress.identity.service.EmailVerificationResult;
import com.uteexpress.identity.service.EmailVerificationService;
import com.uteexpress.identity.service.OtpCodeGenerator;
import com.uteexpress.identity.service.OtpHashService;
import com.uteexpress.identity.service.PasswordResetResult;
import com.uteexpress.identity.service.PasswordResetService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@Import({Auth04PasswordResetIT.Auth04TestConfiguration.class,
        Auth04PasswordResetIT.AuthFixtureConfiguration.class})
class Auth04PasswordResetIT {
    private static final Instant BASE_TIME = Instant.parse("2026-09-26T03:55:26Z");
    private static final String OLD_PASSWORD = "OldSecret1";
    private static final String NEW_PASSWORD = "NewSecret2";
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PasswordResetService passwordReset;
    @Autowired EmailVerificationService emailVerification;
    @Autowired OtpHashService hashes;
    @Autowired MutableClock clock;
    @Autowired TestOtpCodeGenerator codes;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void resetFixtures() {
        clock.set(BASE_TIME);
        codes.set("123456");
        reset(mailSender);
    }

    @Test
    void migrationValidatesSchemaAndAllowsOnlySupportedOtpPurposes() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        long userId = createUser("constraints04@example.com", "constraints04", "ACTIVE");
        String hash = "a".repeat(64);
        insertToken(userId, "EMAIL_VERIFICATION", hash, BASE_TIME, BASE_TIME.plusSeconds(600));
        insertToken(userId, "RESET_PASSWORD", hash, BASE_TIME, BASE_TIME.plusSeconds(600));
        assertThatThrownBy(() -> insertToken(
                userId, "MFA_LOGIN", hash, BASE_TIME, BASE_TIME.plusSeconds(600)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForList("""
                select distinct purpose from uteexpress.otp_tokens
                 where user_id = ? order by purpose
                """, String.class, userId))
                .containsExactly("EMAIL_VERIFICATION", "RESET_PASSWORD");
    }

    @Test
    void fullResetFlowChangesBcryptPasswordConsumesOtpAndRevokesOldJwt() throws Exception {
        long userId = createUser("full-reset@example.com", "full-reset", "ACTIVE");
        Cookie oldJwt = login("full-reset", OLD_PASSWORD);
        long previousTokenVersion = tokenVersion(userId);

        mvc.perform(post("/forgot-password").with(csrf())
                        .param("email", "FULL-RESET@EXAMPLE.COM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password?requested=true"));

        var mail = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("full-reset@example.com");
        assertThat(mail.getValue().getSubject()).contains("đặt lại mật khẩu");
        String code = extractCode(mail.getValue());
        assertThat(latestHash(userId, "RESET_PASSWORD")).doesNotContain(code);

        mvc.perform(post("/reset-password").with(csrf())
                        .param("email", "full-reset@example.com")
                        .param("code", code)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordReset=true"));

        String passwordHash = passwordHash(userId);
        assertThat(passwordHash).isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash)).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash)).isFalse();
        assertThat(tokenVersion(userId)).isGreaterThan(previousTokenVersion);
        assertThat(latestConsumed(userId, "RESET_PASSWORD")).isTrue();

        mvc.perform(get("/test-fixtures/auth04/account").cookie(oldJwt))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", "full-reset")
                        .param("password", OLD_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Không thể đăng nhập với thông tin đã cung cấp.")))
                .andExpect(cookie().doesNotExist("UTEEXPRESS_AUTH"));

        Cookie newJwt = login("full-reset", NEW_PASSWORD);
        mvc.perform(get("/test-fixtures/auth04/account").cookie(newJwt))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(userId)));

        assertThat(passwordReset.resetPassword(
                "full-reset@example.com", code, "AnotherSecret3"))
                .isEqualTo(PasswordResetResult.INVALID);
        assertThat(passwordHash(userId)).isEqualTo(passwordHash);
    }

    @Test
    void forgotPasswordIsEnumerationSafeAndOnlyActiveAccountGetsToken() throws Exception {
        long activeId = createUser("eligible04@example.com", "eligible04", "ACTIVE");
        createUser("pending04@example.com", "pending04", "PENDING_VERIFICATION");
        createUser("locked04@example.com", "locked04", "LOCKED");
        createUser("disabled04@example.com", "disabled04", "DISABLED");

        for (String email : new String[]{"eligible04@example.com", "pending04@example.com",
                "locked04@example.com", "disabled04@example.com", "unknown04@example.com"}) {
            mvc.perform(post("/forgot-password").with(csrf()).param("email", email))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/reset-password?requested=true"));
        }

        assertThat(resetTokenCount(activeId)).isOne();
        assertThat(jdbc.queryForObject("""
                select count(*) from uteexpress.otp_tokens t
                join uteexpress.users u on u.id = t.user_id
                where t.purpose = 'RESET_PASSWORD'
                  and u.normalized_email in (?, ?, ?)
                """, Long.class,
                "pending04@example.com", "locked04@example.com", "disabled04@example.com"))
                .isZero();
    }

    @Test
    void attemptsExpiryCooldownAndLatestOtpSemanticsAreEnforced() {
        long attemptsId = createUser("attempts04@example.com", "attempts04", "ACTIVE");
        codes.set("111111");
        passwordReset.sendResetCode("attempts04@example.com");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(passwordReset.resetPassword(
                    "attempts04@example.com", "000000", NEW_PASSWORD))
                    .isEqualTo(PasswordResetResult.INVALID);
        }
        assertThat(latestAttempts(attemptsId, "RESET_PASSWORD")).isEqualTo(5);
        assertThat(passwordReset.resetPassword(
                "attempts04@example.com", "111111", NEW_PASSWORD))
                .isEqualTo(PasswordResetResult.INVALID);

        createUser("expired04@example.com", "expired04", "ACTIVE");
        codes.set("222222");
        passwordReset.sendResetCode("expired04@example.com");
        clock.advance(Duration.ofMinutes(11));
        assertThat(passwordReset.resetPassword(
                "expired04@example.com", "222222", NEW_PASSWORD))
                .isEqualTo(PasswordResetResult.INVALID);

        clock.set(BASE_TIME);
        long latestId = createUser("latest04@example.com", "latest04", "ACTIVE");
        codes.set("333333");
        passwordReset.sendResetCode("latest04@example.com");
        passwordReset.sendResetCode("latest04@example.com");
        assertThat(resetTokenCount(latestId)).isOne();
        clock.advance(Duration.ofSeconds(61));
        codes.set("444444");
        passwordReset.sendResetCode("latest04@example.com");
        assertThat(resetTokenCount(latestId)).isEqualTo(2);
        assertThat(passwordReset.resetPassword(
                "latest04@example.com", "333333", NEW_PASSWORD))
                .isEqualTo(PasswordResetResult.INVALID);
        assertThat(passwordReset.resetPassword(
                "latest04@example.com", "444444", NEW_PASSWORD))
                .isEqualTo(PasswordResetResult.RESET);
    }

    @Test
    void emailVerificationAndResetOtpPurposesCannotCrossAuthorize() {
        long pendingId = createUser("pending-purpose@example.com", "pending-purpose",
                "PENDING_VERIFICATION");
        String resetHash = hashes.hash(pendingId, OtpPurpose.RESET_PASSWORD, "555555");
        insertToken(pendingId, "RESET_PASSWORD", resetHash, BASE_TIME, BASE_TIME.plusSeconds(600));
        assertThat(emailVerification.verify("pending-purpose", "555555"))
                .isEqualTo(EmailVerificationResult.INVALID);
        assertThat(userStatus(pendingId)).isEqualTo("PENDING_VERIFICATION");

        long activeId = createUser("active-purpose@example.com", "active-purpose", "ACTIVE");
        String verificationHash = hashes.hash(
                activeId, OtpPurpose.EMAIL_VERIFICATION, "666666");
        insertToken(activeId, "EMAIL_VERIFICATION", verificationHash,
                BASE_TIME, BASE_TIME.plusSeconds(600));
        String oldHash = passwordHash(activeId);
        assertThat(passwordReset.resetPassword(
                "active-purpose@example.com", "666666", NEW_PASSWORD))
                .isEqualTo(PasswordResetResult.INVALID);
        assertThat(passwordHash(activeId)).isEqualTo(oldHash);
    }

    @Test
    void concurrentUseOfOneOtpSucceedsAtMostOnce() throws Exception {
        long userId = createUser("concurrent04@example.com", "concurrent04", "ACTIVE");
        codes.set("777777");
        passwordReset.sendResetCode("concurrent04@example.com");

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return passwordReset.resetPassword(
                        "concurrent04@example.com", "777777", NEW_PASSWORD);
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return passwordReset.resetPassword(
                        "concurrent04@example.com", "777777", NEW_PASSWORD);
            });
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(PasswordResetResult.RESET, PasswordResetResult.INVALID);
        }

        assertThat(latestConsumed(userId, "RESET_PASSWORD")).isTrue();
        assertThat(tokenVersion(userId)).isOne();
    }

    private Cookie login(String identifier, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf())
                        .param("identifier", identifier)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }

    private long createUser(String email, String username, String status) {
        Long userId = jdbc.queryForObject("""
                insert into uteexpress.users
                    (email, normalized_email, username, normalized_username, password_hash,
                     status, email_verified_at)
                values (?, ?, ?, ?, ?, ?, case when ? = 'ACTIVE' then CURRENT_TIMESTAMP else null end)
                returning id
                """, Long.class,
                email, email.trim().toLowerCase(Locale.ROOT),
                username, username.trim().toLowerCase(Locale.ROOT),
                passwordEncoder.encode(OLD_PASSWORD), status, status);
        jdbc.update("""
                insert into uteexpress.user_roles (user_id, role_id)
                select ?, id from uteexpress.roles where code = 'USER'
                """, userId);
        return userId;
    }

    private void insertToken(long userId, String purpose, String hash,
            Instant sentAt, Instant expiresAt) {
        jdbc.update("""
                insert into uteexpress.otp_tokens
                    (user_id, purpose, code_hash, sent_at, expires_at, attempts)
                values (?, ?, ?, ?, ?, 0)
                """, userId, purpose, hash,
                OffsetDateTime.ofInstant(sentAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    private String passwordHash(long userId) {
        return jdbc.queryForObject(
                "select password_hash from uteexpress.users where id = ?", String.class, userId);
    }

    private long tokenVersion(long userId) {
        return jdbc.queryForObject(
                "select token_version from uteexpress.users where id = ?", Long.class, userId);
    }

    private String userStatus(long userId) {
        return jdbc.queryForObject(
                "select status from uteexpress.users where id = ?", String.class, userId);
    }

    private long resetTokenCount(long userId) {
        return jdbc.queryForObject("""
                select count(*) from uteexpress.otp_tokens
                 where user_id = ? and purpose = 'RESET_PASSWORD'
                """, Long.class, userId);
    }

    private int latestAttempts(long userId, String purpose) {
        return jdbc.queryForObject("""
                select attempts from uteexpress.otp_tokens
                 where user_id = ? and purpose = ?
                 order by sent_at desc, id desc limit 1
                """, Integer.class, userId, purpose);
    }

    private boolean latestConsumed(long userId, String purpose) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select consumed_at is not null from uteexpress.otp_tokens
                 where user_id = ? and purpose = ?
                 order by sent_at desc, id desc limit 1
                """, Boolean.class, userId, purpose));
    }

    private String latestHash(long userId, String purpose) {
        return jdbc.queryForObject("""
                select code_hash from uteexpress.otp_tokens
                 where user_id = ? and purpose = ?
                 order by sent_at desc, id desc limit 1
                """, String.class, userId, purpose);
    }

    private String extractCode(SimpleMailMessage message) {
        var matcher = SIX_DIGITS.matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Auth04TestConfiguration {
        @Bean
        @Primary
        MutableClock auth04Clock() {
            return new MutableClock(BASE_TIME);
        }

        @Bean
        @Primary
        TestOtpCodeGenerator auth04OtpCodeGenerator() {
            return new TestOtpCodeGenerator();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthFixtureConfiguration {
        @Bean
        AuthFixtureController auth04FixtureController(CurrentAccountIdProvider accountIds) {
            return new AuthFixtureController(accountIds);
        }
    }

    @RestController
    static class AuthFixtureController {
        private final CurrentAccountIdProvider accountIds;

        AuthFixtureController(CurrentAccountIdProvider accountIds) {
            this.accountIds = accountIds;
        }

        @GetMapping("/test-fixtures/auth04/account")
        String accountId() {
            return String.valueOf(accountIds.requireCurrentAccountId());
        }
    }

    static final class TestOtpCodeGenerator implements OtpCodeGenerator {
        private final AtomicReference<String> code = new AtomicReference<>("123456");

        void set(String value) {
            code.set(value);
        }

        @Override
        public String generate() {
            return code.get();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            instant.set(value);
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("AUTH-04 tests use UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
