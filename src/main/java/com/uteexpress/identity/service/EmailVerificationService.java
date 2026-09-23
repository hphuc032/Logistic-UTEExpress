package com.uteexpress.identity.service;

import com.uteexpress.identity.entity.OtpPurpose;
import com.uteexpress.identity.entity.OtpTokenEntity;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.OtpTokenRepository;
import com.uteexpress.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class EmailVerificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final OtpPurpose PURPOSE = OtpPurpose.EMAIL_VERIFICATION;

    private final UserRepository users;
    private final OtpTokenRepository tokens;
    private final OtpCodeGenerator codeGenerator;
    private final OtpHashService hashes;
    private final VerificationMailService mail;
    private final OtpProperties properties;
    private final Clock clock;
    private final TransactionTemplate issueTransaction;

    public EmailVerificationService(UserRepository users, OtpTokenRepository tokens,
            OtpCodeGenerator codeGenerator, OtpHashService hashes,
            VerificationMailService mail, OtpProperties properties, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.tokens = tokens;
        this.codeGenerator = codeGenerator;
        this.hashes = hashes;
        this.mail = mail;
        this.properties = properties;
        this.clock = clock;
        this.issueTransaction = new TransactionTemplate(transactionManager);
    }

    public EmailDispatchResult sendVerificationCode(String identifier) {
        final OtpDelivery delivery;
        try {
            delivery = issueTransaction.execute(status -> issue(identifier));
        } catch (DataAccessException exception) {
            LOGGER.warn("OTP issuance failed because persistence was unavailable");
            return EmailDispatchResult.DELIVERY_FAILED;
        }
        if (delivery == null) {
            return EmailDispatchResult.NO_ACTION;
        }
        try {
            mail.send(delivery.email(), delivery.code(), properties.ttl());
            return EmailDispatchResult.SENT;
        } catch (MailException | IllegalArgumentException exception) {
            LOGGER.warn("OTP email delivery failed for userId={}", delivery.userId());
            return EmailDispatchResult.DELIVERY_FAILED;
        }
    }

    @Transactional
    public EmailVerificationResult verify(String identifier, String code) {
        if (identifier == null || identifier.isBlank()
                || code == null || !code.matches("^[0-9]{6}$")) {
            return EmailVerificationResult.INVALID;
        }
        Optional<UserEntity> resolved = findUserForUpdate(normalize(identifier));
        if (resolved.isEmpty() || resolved.get().getStatus() != UserStatus.PENDING_VERIFICATION) {
            return EmailVerificationResult.INVALID;
        }

        UserEntity user = resolved.get();
        Optional<OtpTokenEntity> latest = tokens
                .findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(user.getId(), PURPOSE);
        if (latest.isEmpty()) {
            return EmailVerificationResult.INVALID;
        }

        OtpTokenEntity token = latest.get();
        Instant now = clock.instant();
        if (token.isConsumed() || token.isExpired(now)
                || token.hasReachedAttemptLimit(properties.maxAttempts())) {
            return EmailVerificationResult.INVALID;
        }
        if (!hashes.matches(token.getCodeHash(), user.getId(), PURPOSE, code)) {
            token.recordFailedAttempt();
            return EmailVerificationResult.INVALID;
        }

        token.consume(now);
        user.activateEmail(now);
        return EmailVerificationResult.VERIFIED;
    }

    private OtpDelivery issue(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        Optional<UserEntity> resolved = findUserForUpdate(normalize(identifier));
        if (resolved.isEmpty() || resolved.get().getStatus() != UserStatus.PENDING_VERIFICATION) {
            return null;
        }

        UserEntity user = resolved.get();
        Instant now = clock.instant();
        Optional<OtpTokenEntity> latest = tokens
                .findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(user.getId(), PURPOSE);
        if (latest.isPresent()
                && now.isBefore(latest.get().getSentAt().plus(properties.resendCooldown()))) {
            return null;
        }

        String code = codeGenerator.generate();
        String codeHash = hashes.hash(user.getId(), PURPOSE, code);
        tokens.save(OtpTokenEntity.issue(
                user, PURPOSE, codeHash, now, now.plus(properties.ttl())));
        return new OtpDelivery(user.getId(), user.getEmail(), code);
    }

    private Optional<UserEntity> findUserForUpdate(String normalizedIdentifier) {
        if (normalizedIdentifier.indexOf('@') >= 0) {
            return users.findByNormalizedEmailForUpdate(normalizedIdentifier);
        }
        return users.findByNormalizedUsernameForUpdate(normalizedIdentifier);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record OtpDelivery(Long userId, String email, String code) {
        @Override
        public String toString() {
            return "OtpDelivery[userId=" + userId + "]";
        }
    }
}
