package com.uteexpress.identity.service;

import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.UserRepository;
import com.uteexpress.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityAuthenticationServiceTest {
    @Mock UserRepository users;
    @Mock UserRoleRepository userRoles;
    @Mock UserEntity user;

    private IdentityAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new IdentityAuthenticationService(users, userRoles);
    }

    @Test
    void normalizesIdentifierAndReturnsInternalAuthenticationSnapshot() {
        given(users.findByNormalizedEmailOrNormalizedUsername("test@example.com", "test@example.com"))
                .willReturn(Optional.of(user));
        given(user.getId()).willReturn(42L);
        given(user.getUsername()).willReturn("Phuc03");
        given(user.getPasswordHash()).willReturn("bcrypt");
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        given(user.getTokenVersion()).willReturn(3L);
        given(userRoles.findRoleCodes(42L)).willReturn(Set.of("USER", "VENDOR"));

        var snapshot = service.findByLoginIdentifier("  Test@Example.com ").orElseThrow();

        assertThat(snapshot.userId()).isEqualTo(42L);
        assertThat(snapshot.displayUsername()).isEqualTo("Phuc03");
        assertThat(snapshot.passwordHash()).isEqualTo("bcrypt");
        assertThat(snapshot.active()).isTrue();
        assertThat(snapshot.tokenVersion()).isEqualTo(3L);
        assertThat(snapshot.roleCodes()).containsExactlyInAnyOrder("USER", "VENDOR");
    }

    @Test
    void invalidationUsesAtomicRepositoryIncrement() {
        given(users.incrementTokenVersion(42L)).willReturn(1);

        assertThat(service.invalidateTokens(42L)).isTrue();
        verify(users).incrementTokenVersion(42L);
    }
}
