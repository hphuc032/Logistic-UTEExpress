package com.uteexpress.identity.service;

import com.uteexpress.identity.entity.OtpPurpose;
import com.uteexpress.identity.entity.OtpTokenEntity;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.repository.OtpTokenRepository;
import com.uteexpress.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T03:55:26Z");
    private static final String PEPPER =
            "VVRFRXhwcmVzcy1BVVRILTAzLXRlc3Qtb25seS1wZXBwZXIta2V5IQ==";

    @Mock UserRepository users;
    @Mock OtpTokenRepository tokens;
    @Mock OtpMailService mail;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PlatformTransactionManager transactionManager;
    @Mock TransactionStatus transactionStatus;

    private OtpProperties properties;
    private OtpHashService hashes;

    @BeforeEach
    void setUp() {
        properties = new OtpProperties(Duration.ofMinutes(10), Duration.ofMinutes(1), 5, PEPPER);
        hashes = new OtpHashService(properties);
    }

    @Test
    void issuanceNormalizesEmailPersistsResetPurposeHashThenMailsPersistedAddress() {
        stubTransactions();
        UserEntity user = activeUser(42L, "Persisted@Example.com", "reset-user");
        given(users.findByNormalizedEmailForUpdate("persisted@example.com"))
                .willReturn(Optional.of(user));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.RESET_PASSWORD)).willReturn(Optional.empty());

        assertThat(service(() -> "004271").sendResetCode("  PERSISTED@EXAMPLE.COM "))
                .isEqualTo(EmailDispatchResult.SENT);

        ArgumentCaptor<OtpTokenEntity> captured = ArgumentCaptor.forClass(OtpTokenEntity.class);
        verify(tokens).save(captured.capture());
        assertThat(captured.getValue().getPurpose()).isEqualTo(OtpPurpose.RESET_PASSWORD);
        assertThat(captured.getValue().getCodeHash())
                .isNotEqualTo("004271")
                .matches("[0-9a-f]{64}");
        verify(mail).sendPasswordReset(
                "Persisted@Example.com", "004271", Duration.ofMinutes(10));
    }

    @Test
    void unknownInactiveAndCooldownRequestsIssueNothing() {
        stubTransactions();
        UserEntity pending = pendingUser(42L, "pending@example.com", "pending-reset");
        given(users.findByNormalizedEmailForUpdate("pending@example.com"))
                .willReturn(Optional.of(pending));
        assertThat(service(() -> "123456").sendResetCode("pending@example.com"))
                .isEqualTo(EmailDispatchResult.NO_ACTION);

        UserEntity active = activeUser(43L, "active@example.com", "active-reset");
        OtpTokenEntity recent = token(active, "222222", NOW.minusSeconds(30), NOW.plusSeconds(570));
        given(users.findByNormalizedEmailForUpdate("active@example.com"))
                .willReturn(Optional.of(active));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                43L, OtpPurpose.RESET_PASSWORD)).willReturn(Optional.of(recent));
        assertThat(service(() -> "333333").sendResetCode("active@example.com"))
                .isEqualTo(EmailDispatchResult.NO_ACTION);
        assertThat(service(() -> "333333").sendResetCode("unknown@example.com"))
                .isEqualTo(EmailDispatchResult.NO_ACTION);

        verify(tokens, never()).save(any());
        verify(mail, never()).sendPasswordReset(any(), any(), any());
    }

    @Test
    void correctResetChangesHashConsumesOtpAndIncrementsTokenVersion() {
        UserEntity user = activeUser(42L, "reset@example.com", "reset-user");
        OtpTokenEntity token = token(user, "123456", NOW, NOW.plusSeconds(600));
        given(users.findByNormalizedEmailForUpdate("reset@example.com"))
                .willReturn(Optional.of(user));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.RESET_PASSWORD)).willReturn(Optional.of(token));
        given(passwordEncoder.encode("NewSecret1")).willReturn("new-bcrypt-hash");

        assertThat(service(() -> "unused").resetPassword(
                "RESET@EXAMPLE.COM", "123456", "NewSecret1"))
                .isEqualTo(PasswordResetResult.RESET);
        assertThat(user.getPasswordHash()).isEqualTo("new-bcrypt-hash");
        assertThat(user.getTokenVersion()).isOne();
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        assertThat(token.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void wrongExpiredConsumedAndAttemptLimitedTokensNeverChangePassword() {
        UserEntity user = activeUser(42L, "reset@example.com", "reset-user");
        OtpTokenEntity token = token(user, "123456", NOW, NOW.plusSeconds(600));
        given(users.findByNormalizedEmailForUpdate("reset@example.com"))
                .willReturn(Optional.of(user));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.RESET_PASSWORD)).willReturn(Optional.of(token));
        PasswordResetService service = service(() -> "unused");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(service.resetPassword("reset@example.com", "000000", "NewSecret1"))
                    .isEqualTo(PasswordResetResult.INVALID);
        }
        assertThat(token.getAttempts()).isEqualTo(5);
        assertThat(service.resetPassword("reset@example.com", "123456", "NewSecret1"))
                .isEqualTo(PasswordResetResult.INVALID);
        verify(passwordEncoder, never()).encode(any());

        UserEntity expiredUser = activeUser(43L, "expired@example.com", "expired-reset");
        OtpTokenEntity expired = token(
                expiredUser, "123456", NOW.minusSeconds(700), NOW.minusSeconds(100));
        given(users.findByNormalizedEmailForUpdate("expired@example.com"))
                .willReturn(Optional.of(expiredUser));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                43L, OtpPurpose.RESET_PASSWORD)).willReturn(Optional.of(expired));
        assertThat(service.resetPassword("expired@example.com", "123456", "NewSecret1"))
                .isEqualTo(PasswordResetResult.INVALID);
    }

    @Test
    void malformedUnknownInactiveAndConsumedResetRequestsAreRejected() {
        PasswordResetService service = service(() -> "unused");
        assertThat(service.resetPassword(
                "reset@example.com", "not-an-otp", "NewSecret1"))
                .isEqualTo(PasswordResetResult.INVALID);

        given(users.findByNormalizedEmailForUpdate("unknown@example.com"))
                .willReturn(Optional.empty());
        assertThat(service.resetPassword(
                "unknown@example.com", "123456", "NewSecret1"))
                .isEqualTo(PasswordResetResult.INVALID);

        UserEntity pending = pendingUser(41L, "pending@example.com", "pending-user");
        given(users.findByNormalizedEmailForUpdate("pending@example.com"))
                .willReturn(Optional.of(pending));
        assertThat(service.resetPassword(
                "pending@example.com", "123456", "NewSecret1"))
                .isEqualTo(PasswordResetResult.INVALID);

        UserEntity active = activeUser(42L, "consumed@example.com", "consumed-user");
        OtpTokenEntity consumed = token(active, "123456", NOW, NOW.plusSeconds(600));
        consumed.consume(NOW.minusSeconds(1));
        given(users.findByNormalizedEmailForUpdate("consumed@example.com"))
                .willReturn(Optional.of(active));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.RESET_PASSWORD)).willReturn(Optional.of(consumed));
        assertThat(service.resetPassword(
                "consumed@example.com", "123456", "NewSecret1"))
                .isEqualTo(PasswordResetResult.INVALID);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPurposeCannotMatchEmailVerificationHash() {
        UserEntity user = activeUser(42L, "reset@example.com", "reset-user");
        String verificationHash = hashes.hash(42L, OtpPurpose.EMAIL_VERIFICATION, "123456");
        OtpTokenEntity wrongPurposeHash = OtpTokenEntity.issue(
                user, OtpPurpose.RESET_PASSWORD, verificationHash, NOW, NOW.plusSeconds(600));
        given(users.findByNormalizedEmailForUpdate("reset@example.com"))
                .willReturn(Optional.of(user));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.RESET_PASSWORD)).willReturn(Optional.of(wrongPurposeHash));

        assertThat(service(() -> "unused").resetPassword(
                "reset@example.com", "123456", "NewSecret1"))
                .isEqualTo(PasswordResetResult.INVALID);
        assertThat(wrongPurposeHash.getAttempts()).isOne();
    }

    @Test
    void serviceRejectsPasswordsOutsideRegistrationPolicyBeforeEncoding() {
        assertThat(service(() -> "unused").resetPassword(
                "reset@example.com", "123456", "short"))
                .isEqualTo(PasswordResetResult.INVALID);
        assertThat(service(() -> "unused").resetPassword(
                "reset@example.com", "123456", "🙂".repeat(19)))
                .isEqualTo(PasswordResetResult.INVALID);

        verify(users, never()).findByNormalizedEmailForUpdate(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private PasswordResetService service(OtpCodeGenerator generator) {
        return new PasswordResetService(users, tokens, generator, hashes, mail, properties,
                passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager);
    }

    private void stubTransactions() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willReturn(transactionStatus);
    }

    private UserEntity activeUser(long id, String email, String username) {
        UserEntity user = pendingUser(id, email, username);
        user.activateEmail(NOW.minusSeconds(3600));
        return user;
    }

    private UserEntity pendingUser(long id, String email, String username) {
        UserEntity user = UserEntity.pendingRegistration(
                email, email.toLowerCase(), username, username.toLowerCase(), "old-hash", NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private OtpTokenEntity token(UserEntity user, String code, Instant sentAt, Instant expiresAt) {
        return OtpTokenEntity.issue(user, OtpPurpose.RESET_PASSWORD,
                hashes.hash(user.getId(), OtpPurpose.RESET_PASSWORD, code), sentAt, expiresAt);
    }
}
