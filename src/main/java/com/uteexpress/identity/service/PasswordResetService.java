package com.uteexpress.identity.service;

import com.uteexpress.identity.entity.OtpPurpose;
import com.uteexpress.identity.entity.OtpTokenEntity;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.OtpTokenRepository;
import com.uteexpress.identity.repository.UserRepository;
import com.uteexpress.identity.validation.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final OtpPurpose PURPOSE = OtpPurpose.RESET_PASSWORD;

    private final UserRepository users;
    private final OtpTokenRepository tokens;
    private final OtpCodeGenerator codeGenerator;
    private final OtpHashService hashes;
    private final OtpMailService mail;
    private final OtpProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate issueTransaction;

    public PasswordResetService(UserRepository users, OtpTokenRepository tokens,
            OtpCodeGenerator codeGenerator, OtpHashService hashes, OtpMailService mail,
            OtpProperties properties, PasswordEncoder passwordEncoder, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.tokens = tokens;
        this.codeGenerator = codeGenerator;
        this.hashes = hashes;
        this.mail = mail;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.issueTransaction = new TransactionTemplate(transactionManager);
    }

    public EmailDispatchResult sendResetCode(String email) {
        final OtpDelivery delivery;
        try {
            delivery = issueTransaction.execute(status -> issue(email));
        } catch (DataAccessException exception) {
            LOGGER.warn("Password reset OTP issuance failed because persistence was unavailable");
            return EmailDispatchResult.DELIVERY_FAILED;
        }
        if (delivery == null) {
            return EmailDispatchResult.NO_ACTION;
        }
        try {
            mail.sendPasswordReset(delivery.email(), delivery.code(), properties.ttl());
            return EmailDispatchResult.SENT;
        } catch (MailException | IllegalArgumentException exception) {
            LOGGER.warn("Password reset email delivery failed for userId={}", delivery.userId());
            return EmailDispatchResult.DELIVERY_FAILED;
        }
    }

    @Transactional
    public PasswordResetResult resetPassword(String email, String code, String newPassword) {
        if (email == null || email.isBlank()
                || code == null || !code.matches("^[0-9]{6}$")
                || !PasswordPolicy.isValid(newPassword)) {
            return PasswordResetResult.INVALID;
        }

        Optional<UserEntity> resolved = users.findByNormalizedEmailForUpdate(normalize(email));
        if (resolved.isEmpty() || resolved.get().getStatus() != UserStatus.ACTIVE) {
            return PasswordResetResult.INVALID;
        }

        UserEntity user = resolved.get();
        Optional<OtpTokenEntity> latest = tokens
                .findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(user.getId(), PURPOSE);
        if (latest.isEmpty()) {
            return PasswordResetResult.INVALID;
        }

        OtpTokenEntity token = latest.get();
        Instant now = clock.instant();
        if (token.isConsumed() || token.isExpired(now)
                || token.hasReachedAttemptLimit(properties.maxAttempts())) {
            return PasswordResetResult.INVALID;
        }
        if (!hashes.matches(token.getCodeHash(), user.getId(), PURPOSE, code)) {
            token.recordFailedAttempt();
            return PasswordResetResult.INVALID;
        }

        user.resetPassword(passwordEncoder.encode(newPassword), now);
        token.consume(now);
        return PasswordResetResult.RESET;
    }

    private OtpDelivery issue(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        Optional<UserEntity> resolved = users.findByNormalizedEmailForUpdate(normalize(email));
        if (resolved.isEmpty() || resolved.get().getStatus() != UserStatus.ACTIVE) {
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
