package com.uteexpress.identity.service;

import com.uteexpress.identity.dto.RegistrationCommand;
import com.uteexpress.identity.dto.RegistrationOutcome;
import com.uteexpress.identity.entity.RoleEntity;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.repository.RoleRepository;
import com.uteexpress.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class RegistrationService {
    private static final String DEFAULT_ROLE_CODE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationOutcome register(RegistrationCommand command) {
        String email = command.email().trim();
        String username = command.username().trim();
        String normalizedEmail = normalize(email);
        String normalizedUsername = normalize(username);

        if (userRepository.existsByNormalizedEmail(normalizedEmail)
                || userRepository.existsByNormalizedUsername(normalizedUsername)) {
            throw new RegistrationConflictException();
        }

        RoleEntity userRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Required registration role is missing."));
        String passwordHash = passwordEncoder.encode(command.password());
        UserEntity user = UserEntity.pendingRegistration(
                email, normalizedEmail, username, normalizedUsername, passwordHash, Instant.now());
        user.assignRole(userRole);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new RegistrationConflictException();
        }
        return new RegistrationOutcome(user.getUsername());
    }

    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
