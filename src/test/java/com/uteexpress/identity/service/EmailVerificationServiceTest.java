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
import org.springframework.mail.MailSendException;
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
class EmailVerificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final String PEPPER =
            "VVRFRXhwcmVzcy1BVVRILTAzLXRlc3Qtb25seS1wZXBwZXIta2V5IQ==";

    @Mock UserRepository users;
    @Mock OtpTokenRepository tokens;
    @Mock OtpMailService mail;
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
    void issuancePersistsOnlyHmacThenMailsPersistedAddressAfterCommit() {
        stubTransactions();
        UserEntity user = pendingUser(42L, "persisted@example.com", "Phuc03");
        given(users.findByNormalizedUsernameForUpdate("phuc03")).willReturn(Optional.of(user));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.EMAIL_VERIFICATION)).willReturn(Optional.empty());
        EmailVerificationService service = service(() -> "004271");

        assertThat(service.sendVerificationCode("  PHUC03 ")).isEqualTo(EmailDispatchResult.SENT);

        ArgumentCaptor<OtpTokenEntity> token = ArgumentCaptor.forClass(OtpTokenEntity.class);
        verify(tokens).save(token.capture());
        assertThat(token.getValue().getCodeHash())
                .isNotEqualTo("004271")
                .matches("[0-9a-f]{64}");
        verify(transactionManager).commit(transactionStatus);
        verify(mail).sendEmailVerification(
                "persisted@example.com", "004271", Duration.ofMinutes(10));
    }

    @Test
    void cooldownAndIneligibleAccountsCreateNoTokenAndSendNoMail() {
        stubTransactions();
        UserEntity pending = pendingUser(42L, "persisted@example.com", "Phuc03");
        OtpTokenEntity latest = OtpTokenEntity.issue(pending, OtpPurpose.EMAIL_VERIFICATION,
                hashes.hash(42L, OtpPurpose.EMAIL_VERIFICATION, "111111"),
                NOW.minusSeconds(30), NOW.plusSeconds(570));
        given(users.findByNormalizedUsernameForUpdate("phuc03")).willReturn(Optional.of(pending));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.EMAIL_VERIFICATION)).willReturn(Optional.of(latest));

        assertThat(service(() -> "222222").sendVerificationCode("Phuc03"))
                .isEqualTo(EmailDispatchResult.NO_ACTION);
        assertThat(service(() -> "222222").sendVerificationCode("missing"))
                .isEqualTo(EmailDispatchResult.NO_ACTION);
        verify(tokens, never()).save(any());
        verify(mail, never()).sendEmailVerification(any(), any(), any());
    }

    @Test
    void smtpFailureDoesNotRollBackPersistedOtp() {
        stubTransactions();
        UserEntity user = pendingUser(42L, "persisted@example.com", "Phuc03");
        given(users.findByNormalizedUsernameForUpdate("phuc03")).willReturn(Optional.of(user));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.EMAIL_VERIFICATION)).willReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new MailSendException("smtp unavailable"))
                .when(mail).sendEmailVerification(any(), any(), any());

        assertThat(service(() -> "123456").sendVerificationCode("Phuc03"))
                .isEqualTo(EmailDispatchResult.DELIVERY_FAILED);
        verify(tokens).save(any(OtpTokenEntity.class));
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void verificationTracksAttemptsAndActivatesOnlyWithUsableMatchingToken() {
        UserEntity user = pendingUser(42L, "persisted@example.com", "Phuc03");
        OtpTokenEntity token = OtpTokenEntity.issue(user, OtpPurpose.EMAIL_VERIFICATION,
                hashes.hash(42L, OtpPurpose.EMAIL_VERIFICATION, "123456"),
                NOW, NOW.plusSeconds(600));
        given(users.findByNormalizedUsernameForUpdate("phuc03")).willReturn(Optional.of(user));
        given(tokens.findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
                42L, OtpPurpose.EMAIL_VERIFICATION)).willReturn(Optional.of(token));
        EmailVerificationService service = service(() -> "unused");

        assertThat(service.verify("Phuc03", "000000")).isEqualTo(EmailVerificationResult.INVALID);
        assertThat(token.getAttempts()).isOne();
        assertThat(service.verify("Phuc03", "123456")).isEqualTo(EmailVerificationResult.VERIFIED);
        assertThat(user.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(token.getConsumedAt()).isEqualTo(NOW);
        assertThat(service.verify("Phuc03", "123456")).isEqualTo(EmailVerificationResult.INVALID);
    }

    private EmailVerificationService service(OtpCodeGenerator generator) {
        return new EmailVerificationService(users, tokens, generator, hashes, mail, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), transactionManager);
    }

    private void stubTransactions() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willReturn(transactionStatus);
    }

    private UserEntity pendingUser(long id, String email, String username) {
        UserEntity user = UserEntity.pendingRegistration(
                email, email.toLowerCase(), username, username.toLowerCase(), "hash", NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
