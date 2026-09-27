package com.uteexpress.identity.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.dto.AccountProfileData;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.UserRepository;
import com.uteexpress.identity.validation.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccountIdentityService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountIdentityService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountProfileData getProfile(Long userId) {
        return snapshot(activeUser(userId));
    }

    @Transactional
    public AccountProfileData updateProfile(Long userId, String fullName, String phone) {
        UserEntity user = activeUserForUpdate(userId);
        user.updateProfile(blankToNull(fullName), blankToNull(phone), Instant.now(clock));
        return snapshot(user);
    }

    @Transactional
    public String replaceAvatarKey(Long userId, String avatarKey) {
        if (avatarKey == null || avatarKey.isBlank() || avatarKey.length() > 512) {
            throw new IllegalArgumentException("A valid avatar storage key is required");
        }
        return activeUserForUpdate(userId).replaceAvatarKey(avatarKey, Instant.now(clock));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new IllegalArgumentException("New password does not satisfy the password policy");
        }
        UserEntity user = activeUserForUpdate(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CurrentPasswordMismatchException();
        }
        user.changePassword(passwordEncoder.encode(newPassword), Instant.now(clock));
    }

    private UserEntity activeUser(Long userId) {
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return requireActive(user);
    }

    private UserEntity activeUserForUpdate(Long userId) {
        UserEntity user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return requireActive(user);
    }

    private static UserEntity requireActive(UserEntity user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return user;
    }

    private static AccountProfileData snapshot(UserEntity user) {
        return new AccountProfileData(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getPhone(), user.getAvatarKey());
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
