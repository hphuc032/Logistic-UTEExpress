package com.uteexpress.security.authentication;

import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UteExpressUserDetailsServiceTest {
    @Mock IdentityAuthenticationService identities;
    private final UteExpressPrincipalFactory principalFactory = new UteExpressPrincipalFactory();

    @Test
    void loadsStablePrincipalAndValidatedAuthorities() {
        given(identities.findByLoginIdentifier("Phuc03")).willReturn(Optional.of(
                new AuthAccountSnapshot(12L, "Phuc03", "hash", true, 4L,
                        Set.of("USER", "VENDOR"))));
        var service = new UteExpressUserDetailsService(identities, principalFactory);

        UteExpressPrincipal principal = (UteExpressPrincipal) service.loadUserByUsername("Phuc03");

        assertThat(principal.getUsername()).isEqualTo("uteexpress:user:12");
        assertThat(principal.displayUsername()).isEqualTo("Phuc03");
        assertThat(principal.tokenVersion()).isEqualTo(4L);
        assertThat(principal.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_VENDOR");
    }

    @Test
    void unknownIdentityUsesGenericSpringAuthenticationFailure() {
        given(identities.findByLoginIdentifier("missing")).willReturn(Optional.empty());
        var service = new UteExpressUserDetailsService(identities, principalFactory);

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Authentication failed");
    }

    @Test
    void inactiveIdentityUsesSameGenericSpringAuthenticationFailure() {
        given(identities.findByLoginIdentifier("pending")).willReturn(Optional.of(
                new AuthAccountSnapshot(12L, "pending", "hash", false, 0L, Set.of("USER"))));
        var service = new UteExpressUserDetailsService(identities, principalFactory);

        assertThatThrownBy(() -> service.loadUserByUsername("pending"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Authentication failed");
    }

    @Test
    void unknownDatabaseRoleFailsClosed() {
        given(identities.findByLoginIdentifier("user")).willReturn(Optional.of(
                new AuthAccountSnapshot(12L, "user", "hash", true, 0L, Set.of("ROOT"))));
        var service = new UteExpressUserDetailsService(identities, principalFactory);

        assertThatThrownBy(() -> service.loadUserByUsername("user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported role");
    }
}
