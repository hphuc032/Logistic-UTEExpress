package com.uteexpress.security;

import com.uteexpress.security.service.SocketIdentityService;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocketIdentityServiceTest {
    final JwtDecoder decoder = mock(JwtDecoder.class);
    final IdentityAuthenticationService identities = mock(IdentityAuthenticationService.class);
    final ObjectProvider<org.springframework.transaction.PlatformTransactionManager> managers = mock(ObjectProvider.class);
    final SocketIdentityService service = new SocketIdentityService(decoder, mock(JwtProperties.class), identities,
            mock(AccountIdentityService.class), managers, Clock.systemUTC(), mock(ObjectProvider.class));
    void claims(Object subject, Object version) {
        var jwt = mock(Jwt.class);
        var claims = new HashMap<String,Object>(); claims.put("sub",subject); claims.put("tokenVersion",version);
        when(jwt.getClaims()).thenReturn(claims); when(decoder.decode("opaque")).thenReturn(jwt);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"", "wrong:1", "uteexpress:user:", "uteexpress:user:0", "uteexpress:user:-1",
            "uteexpress:user:+1", "uteexpress:user:01", "uteexpress:user: 1", "uteexpress:user:1x",
            "uteexpress:user:9223372036854775808", "uteexpress:user:9999999999999999999999999999999", "uteexpress:user:١"})
    void deniesMalformedSubjectBeforeLookup(String subject) {
        claims(subject,0L);
        assertThatThrownBy(() -> service.authenticate("opaque")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(identities,managers);
    }
    static Stream<Object> versions() { return Stream.of(null,"0",true,-1L,0.0d,0.5d,Double.NaN,Double.POSITIVE_INFINITY,
            new java.math.BigDecimal("0"),new java.math.BigInteger("9223372036854775808")); }
    @ParameterizedTest @MethodSource("versions") void deniesMalformedVersionBeforeLookup(Object version) {
        claims("uteexpress:user:1",version);
        assertThatThrownBy(() -> service.authenticate("opaque")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(identities,managers);
    }
    @Test void deniesNonStringSubjectBeforeLookup() {
        claims(1L,0L);
        assertThatThrownBy(() -> service.authenticate("opaque")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(identities,managers);
    }
    @Test void exactLongBoundariesReachPersistedAuthorityWithoutTruncation() {
        claims("uteexpress:user:9223372036854775807",Long.MAX_VALUE);
        when(identities.findByUserId(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("opaque")).isInstanceOf(AccessDeniedException.class);
        verify(identities).findByUserId(Long.MAX_VALUE);
    }
}
