package com.uteexpress.identity.service;

import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.UserRepository;
import com.uteexpress.identity.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class IdentityAuthenticationService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public IdentityAuthenticationService(UserRepository userRepository,
            UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AuthAccountSnapshot> findByLoginIdentifier(String identifier) {
        String normalized = normalize(identifier);
        return userRepository.findByNormalizedEmailOrNormalizedUsername(normalized, normalized)
                .map(this::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<AuthAccountSnapshot> findByUserId(Long userId) {
        return userRepository.findById(userId).map(this::snapshot);
    }

    @Transactional
    public boolean invalidateTokens(Long userId) {
        return userRepository.incrementTokenVersion(userId) == 1;
    }

    private AuthAccountSnapshot snapshot(UserEntity user) {
        return new AuthAccountSnapshot(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE,
                user.getTokenVersion(),
                userRoleRepository.findRoleCodes(user.getId()));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
