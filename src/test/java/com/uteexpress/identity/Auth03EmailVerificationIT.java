package com.uteexpress.identity;

import com.uteexpress.identity.dto.RegistrationCommand;
import com.uteexpress.identity.service.EmailDispatchResult;
import com.uteexpress.identity.service.EmailVerificationResult;
import com.uteexpress.identity.service.EmailVerificationService;
import com.uteexpress.identity.service.OtpCodeGenerator;
import com.uteexpress.identity.service.RegistrationService;
import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@Import(Auth03EmailVerificationIT.Auth03TestConfiguration.class)
class Auth03EmailVerificationIT {
    private static final Instant BASE_TIME = Instant.parse("2026-09-23T10:00:00Z");
    private static final String RAW_PASSWORD = "RawSecret1";
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired RegistrationService registration;
    @Autowired EmailVerificationService verification;
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
    void migrationAndHibernateValidateOtpSchemaAndDatabaseConstraints() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(tableExists("otp_tokens")).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        long userId = registerPending("constraints@example.com", "otp-constraints");
        String hash = "a".repeat(64);

        assertThatThrownBy(() -> insertToken(9_999_999L, "EMAIL_VERIFICATION", hash,
                BASE_TIME, BASE_TIME.plusSeconds(600), 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertToken(userId, "RESET_PASSWORD", hash,
                BASE_TIME, BASE_TIME.plusSeconds(600), 0);
        assertThatThrownBy(() -> insertToken(userId, "UNSUPPORTED_PURPOSE", hash,
                BASE_TIME, BASE_TIME.plusSeconds(600), 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertToken(userId, "EMAIL_VERIFICATION", hash,
                BASE_TIME, BASE_TIME.plusSeconds(600), -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertToken(userId, "EMAIL_VERIFICATION", hash,
                BASE_TIME, BASE_TIME, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void registerVerifyLoginFlowUsesPersistedEmailAndNeverPersistsRawOtp() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("email", "Acceptance.User@Example.com")
                        .param("username", "acceptance-user")
                        .param("password", RAW_PASSWORD)
                        .param("confirmPassword", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-otp?identifier=acceptance-user"));

        var mail = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("Acceptance.User@Example.com");
        String code = extractCode(mail.getValue());
        assertThat(code).isEqualTo("123456");

        long userId = userId("acceptance-user");
        assertThat(userStatus(userId)).isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbc.queryForObject("""
                select count(*) from uteexpress.otp_tokens
                 where user_id = ? and code_hash = ?
                """, Long.class, userId, code)).isZero();

        mvc.perform(post("/login").with(csrf())
                        .param("identifier", "acceptance-user")
                        .param("password", RAW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Không thể đăng nhập với thông tin đã cung cấp.")))
                .andExpect(cookie().doesNotExist("UTEEXPRESS_AUTH"));

        mvc.perform(post("/verify-otp").with(csrf())
                        .param("identifier", "acceptance-user")
                        .param("code", code))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?verified=true"));

        assertThat(userStatus(userId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("""
                select email_verified_at is not null from uteexpress.users where id = ?
                """, Boolean.class, userId)).isTrue();
        assertThat(jdbc.queryForObject("""
                select consumed_at is not null from uteexpress.otp_tokens
                 where user_id = ? order by sent_at desc, id desc limit 1
                """, Boolean.class, userId)).isTrue();

        mvc.perform(post("/login").with(csrf())
                        .param("identifier", "acceptance-user")
                        .param("password", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"));
    }

    @Test
    void attemptsExpiryCooldownLatestTokenAndStatusRulesAreEnforced() {
        long attemptsUser = registerPending("attempts@example.com", "otp-attempts");
        assertThat(verification.sendVerificationCode("otp-attempts"))
                .isEqualTo(EmailDispatchResult.SENT);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(verification.verify("otp-attempts", "000000"))
                    .isEqualTo(EmailVerificationResult.INVALID);
        }
        assertThat(latestAttempts(attemptsUser)).isEqualTo(5);
        assertThat(verification.verify("otp-attempts", "123456"))
                .isEqualTo(EmailVerificationResult.INVALID);
        assertThat(userStatus(attemptsUser)).isEqualTo("PENDING_VERIFICATION");

        long expiredUser = registerPending("expired@example.com", "otp-expired");
        codes.set("234567");
        assertThat(verification.sendVerificationCode("otp-expired"))
                .isEqualTo(EmailDispatchResult.SENT);
        clock.advance(Duration.ofMinutes(11));
        assertThat(verification.verify("otp-expired", "234567"))
                .isEqualTo(EmailVerificationResult.INVALID);
        assertThat(userStatus(expiredUser)).isEqualTo("PENDING_VERIFICATION");

        clock.set(BASE_TIME);
        long resendUser = registerPending("resend@example.com", "otp-resend");
        codes.set("345678");
        assertThat(verification.sendVerificationCode("otp-resend"))
                .isEqualTo(EmailDispatchResult.SENT);
        long initialCount = tokenCount(resendUser);
        codes.set("456789");
        assertThat(verification.sendVerificationCode("otp-resend"))
                .isEqualTo(EmailDispatchResult.NO_ACTION);
        assertThat(tokenCount(resendUser)).isEqualTo(initialCount);
        clock.advance(Duration.ofSeconds(61));
        assertThat(verification.sendVerificationCode("otp-resend"))
                .isEqualTo(EmailDispatchResult.SENT);
        assertThat(tokenCount(resendUser)).isEqualTo(initialCount + 1);
        assertThat(verification.verify("otp-resend", "345678"))
                .isEqualTo(EmailVerificationResult.INVALID);
        assertThat(verification.verify("otp-resend", "456789"))
                .isEqualTo(EmailVerificationResult.VERIFIED);
        assertThat(verification.verify("otp-resend", "456789"))
                .isEqualTo(EmailVerificationResult.INVALID);
        assertThat(verification.sendVerificationCode("otp-resend"))
                .isEqualTo(EmailDispatchResult.NO_ACTION);

        assertStatusCannotActivate("LOCKED", "locked-otp@example.com", "locked-otp", "567890");
        assertStatusCannotActivate("DISABLED", "disabled-otp@example.com", "disabled-otp", "678901");
    }

    @Test
    void smtpFailureKeepsPendingRegistrationAndResendEndpointAvailable() throws Exception {
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        mvc.perform(post("/register").with(csrf())
                        .param("email", "smtp-failure@example.com")
                        .param("username", "smtp-failure")
                        .param("password", RAW_PASSWORD)
                        .param("confirmPassword", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/verify-otp?identifier=smtp-failure&deliveryFailed=true"));

        long userId = userId("smtp-failure");
        assertThat(userStatus(userId)).isEqualTo("PENDING_VERIFICATION");
        assertThat(tokenCount(userId)).isOne();

        reset(mailSender);
        mvc.perform(post("/verify-otp/resend").with(csrf())
                        .param("identifier", "smtp-failure"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Nếu tài khoản hợp lệ và cần xác minh")));
    }

    @Test
    void concurrentCorrectVerificationActivatesOnceWithoutInconsistentState() throws Exception {
        long userId = registerPending("concurrent@example.com", "otp-concurrent");
        codes.set("789012");
        verification.sendVerificationCode("otp-concurrent");

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return verification.verify("otp-concurrent", "789012");
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return verification.verify("otp-concurrent", "789012");
            });
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            EmailVerificationResult.VERIFIED, EmailVerificationResult.INVALID);
        }

        assertThat(userStatus(userId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("""
                select count(*) from uteexpress.otp_tokens
                 where user_id = ? and consumed_at is not null
                """, Long.class, userId)).isOne();
    }

    private void assertStatusCannotActivate(String accountStatus, String email,
            String username, String code) {
        clock.set(BASE_TIME);
        long userId = registerPending(email, username);
        codes.set(code);
        verification.sendVerificationCode(username);
        jdbc.update("update uteexpress.users set status = ? where id = ?", accountStatus, userId);

        assertThat(verification.verify(username, code)).isEqualTo(EmailVerificationResult.INVALID);
        assertThat(userStatus(userId)).isEqualTo(accountStatus);
    }

    private long registerPending(String email, String username) {
        registration.register(new RegistrationCommand(email, username, RAW_PASSWORD));
        return userId(username);
    }

    private long userId(String username) {
        return jdbc.queryForObject("""
                select id from uteexpress.users where normalized_username = ?
                """, Long.class, username.toLowerCase());
    }

    private String userStatus(long userId) {
        return jdbc.queryForObject(
                "select status from uteexpress.users where id = ?", String.class, userId);
    }

    private int latestAttempts(long userId) {
        return jdbc.queryForObject("""
                select attempts from uteexpress.otp_tokens
                 where user_id = ? order by sent_at desc, id desc limit 1
                """, Integer.class, userId);
    }

    private long tokenCount(long userId) {
        return jdbc.queryForObject(
                "select count(*) from uteexpress.otp_tokens where user_id = ?", Long.class, userId);
    }

    private void insertToken(long userId, String purpose, String hash, Instant sentAt,
            Instant expiresAt, int attempts) {
        jdbc.update("""
                insert into uteexpress.otp_tokens
                    (user_id, purpose, code_hash, sent_at, expires_at, attempts)
                values (?, ?, ?, ?, ?, ?)
                """, userId, purpose, hash,
                OffsetDateTime.ofInstant(sentAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC), attempts);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from information_schema.tables
                     where table_schema = 'uteexpress' and table_name = ?
                )
                """, Boolean.class, tableName));
    }

    private String extractCode(SimpleMailMessage message) {
        var matcher = SIX_DIGITS.matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Auth03TestConfiguration {
        @Bean
        @Primary
        MutableClock auth03Clock() {
            return new MutableClock(BASE_TIME);
        }

        @Bean
        @Primary
        TestOtpCodeGenerator auth03OtpCodeGenerator() {
            return new TestOtpCodeGenerator();
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
            this.instant = new AtomicReference<>(initial);
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
                throw new IllegalArgumentException("AUTH-03 tests use UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
