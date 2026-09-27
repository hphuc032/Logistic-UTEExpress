package com.uteexpress.identity.service;

import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountIdentityServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T08:50:03Z");
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    private AccountIdentityService service;

    @BeforeEach
    void setUp() {
        service = new AccountIdentityService(users, encoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void profileUpdatesOnlyProfileFieldsAndNeverTokenVersion() {
        UserEntity user = activeUser();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));

        var result = service.updateProfile(7L, "  Nguyễn Văn A  ", "  +84 901-234-567 ");

        assertThat(result.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.phone()).isEqualTo("+84 901-234-567");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getUsername()).isEqualTo("profile-user");
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndIncrementsVersionExactlyOnce() {
        UserEntity user = activeUser();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("OldSecret1", "old-hash")).thenReturn(true);
        when(encoder.encode("NewSecret2")).thenReturn("new-hash");

        service.changePassword(7L, "OldSecret1", "NewSecret2");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isOne();
        verify(encoder).matches("OldSecret1", "old-hash");
    }

    @Test
    void wrongCurrentPasswordChangesNothing() {
        UserEntity user = activeUser();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(7L, "wrong", "NewSecret2"))
                .isInstanceOf(CurrentPasswordMismatchException.class);
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        assertThat(user.getTokenVersion()).isZero();
    }

    private static UserEntity activeUser() {
        UserEntity user = UserEntity.pendingRegistration("user@example.com", "user@example.com",
                "profile-user", "profile-user", "old-hash", NOW.minusSeconds(60));
        user.activateEmail(NOW.minusSeconds(30));
        return user;
    }
}
