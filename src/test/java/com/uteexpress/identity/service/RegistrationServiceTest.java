package com.uteexpress.identity.service;

import com.uteexpress.identity.dto.RegistrationCommand;
import com.uteexpress.identity.entity.RoleEntity;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.RoleRepository;
import com.uteexpress.identity.repository.UserRepository;
import com.uteexpress.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RoleEntity userRole;
    @Mock UserEntity savedUser;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(userRepository, roleRepository, userRoleRepository, passwordEncoder);
    }

    @Test
    void normalizesIdentityHashesPasswordAndCreatesPendingUserRoleOnly() {
        given(roleRepository.findByCode("USER")).willReturn(Optional.of(userRole));
        given(passwordEncoder.encode("RawSecret1")).willReturn("bcrypt-hash");
        given(userRepository.saveAndFlush(any(UserEntity.class))).willReturn(savedUser);
        given(savedUser.getId()).willReturn(42L);
        given(savedUser.getUsername()).willReturn("Phuc03");
        given(userRole.getId()).willReturn(7L);

        service.register(new RegistrationCommand("  Test@Example.com ", "Phuc03", "RawSecret1"));

        verify(userRepository).existsByNormalizedEmail("test@example.com");
        verify(userRepository).existsByNormalizedUsername("phuc03");
        verify(passwordEncoder).encode("RawSecret1");
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());

        UserEntity user = captor.getValue();
        assertThat(user.getEmail()).isEqualTo("Test@Example.com");
        assertThat(user.getNormalizedEmail()).isEqualTo("test@example.com");
        assertThat(user.getUsername()).isEqualTo("Phuc03");
        assertThat(user.getNormalizedUsername()).isEqualTo("phuc03");
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash").isNotEqualTo("RawSecret1");
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getTokenVersion()).isZero();
        verify(userRoleRepository).assign(42L, 7L);
    }

    @Test
    void rejectsDuplicateEmailBeforeHashingOrPersisting() {
        given(userRepository.existsByNormalizedEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegistrationCommand("Test@Example.com", "new-user", "RawSecret1")))
                .isInstanceOf(RegistrationConflictException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
        verify(userRoleRepository, never()).assign(any(), any());
    }

    @Test
    void rejectsDuplicateUsernameBeforeHashingOrPersisting() {
        given(userRepository.existsByNormalizedUsername("phuc03")).willReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegistrationCommand("new@example.com", "Phuc03", "RawSecret1")))
                .isInstanceOf(RegistrationConflictException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
        verify(userRoleRepository, never()).assign(any(), any());
    }

    @Test
    void convertsDatabaseUniqueRaceToSafeConflict() {
        given(roleRepository.findByCode("USER")).willReturn(Optional.of(userRole));
        given(passwordEncoder.encode("RawSecret1")).willReturn("bcrypt-hash");
        given(userRepository.saveAndFlush(any(UserEntity.class)))
                .willThrow(new DataIntegrityViolationException("uq_users_normalized_email"));

        assertThatThrownBy(() -> service.register(
                new RegistrationCommand("new@example.com", "new-user", "RawSecret1")))
                .isInstanceOf(RegistrationConflictException.class)
                .hasMessageNotContaining("uq_users_normalized_email");
    }
}
