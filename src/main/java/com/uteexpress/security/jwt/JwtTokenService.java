package com.uteexpress.security.jwt;

import com.uteexpress.security.JwtProperties;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {
    private static final String SUBJECT_PREFIX = "uteexpress:user:";
    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtDecoder decoder,
            JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(UteExpressPrincipal principal) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(principal.subject())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.ttl()))
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .claim(TOKEN_VERSION_CLAIM, principal.tokenVersion())
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public JwtIdentity decode(String token) {
        Jwt jwt = decoder.decode(token);
        String subject = jwt.getSubject();
        Number tokenVersion = jwt.getClaim(TOKEN_VERSION_CLAIM);
        try {
            Long userId = Long.valueOf(subject.substring(SUBJECT_PREFIX.length()));
            return new JwtIdentity(userId, tokenVersion.longValue());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT identity claims are invalid", exception);
        }
    }
}
