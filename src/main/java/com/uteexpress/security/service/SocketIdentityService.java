package com.uteexpress.security.service;

import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.security.JwtProperties;
import com.uteexpress.security.dto.SocketIdentity;
import java.time.Clock;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Account-only transaction; callbacks must never call Order/Shipment or perform database writes. */
@Service
public class SocketIdentityService {
    private static final Set<String> ROLES = Set.of("USER", "VENDOR", "SHIPPER", "MANAGER", "ADMIN");
    private final JwtDecoder decoder;
    private final JwtProperties properties;
    private final IdentityAuthenticationService identities;
    private final AccountIdentityService accounts;
    private final ObjectProvider<PlatformTransactionManager> managers;
    private final Clock clock;

    public SocketIdentityService(JwtDecoder decoder, JwtProperties properties,
            IdentityAuthenticationService identities, AccountIdentityService accounts,
            ObjectProvider<PlatformTransactionManager> managers, Clock clock) {
        this.decoder = decoder; this.properties = properties; this.identities = identities;
        this.accounts = accounts; this.managers = managers; this.clock = clock;
    }

    public String cookieName() { return properties.cookieName(); }

    public SocketIdentity authenticate(String token) {
        var jwt = decoder.decode(token); // existing signature/issuer/audience/claims validators
        var identity = new SocketIdentity(Long.valueOf(jwt.getSubject().substring("uteexpress:user:".length())),
                ((Number) jwt.getClaim("tokenVersion")).longValue(),
                identities.findByUserId(Long.valueOf(jwt.getSubject().substring("uteexpress:user:".length())))
                        .orElseThrow(SocketIdentityService::denied).roleCodes(), jwt.getExpiresAt());
        runIfCurrent(identity, () -> {});
        return identity;
    }

    public void runIfCurrent(SocketIdentity identity, Runnable delivery) {
        if (identity == null || identity.expiresAt() == null || !clock.instant().isBefore(identity.expiresAt())) throw denied();
        var tx = new TransactionTemplate(managers.getObject());
        // After-commit inbox hints can still have completed transaction resources bound.
        // An independent Account-only transaction avoids reusing those resources.
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setTimeout(5);
        tx.executeWithoutResult(status -> {
            accounts.requireActiveAccountForUpdate(identity.accountId());
            var current = identities.findByUserId(identity.accountId()).orElseThrow(SocketIdentityService::denied);
            if (!current.active() || current.tokenVersion() != identity.tokenVersion()
                    || current.roleCodes().isEmpty() || !ROLES.containsAll(current.roleCodes())
                    || !current.roleCodes().equals(identity.roles())
                    || !clock.instant().isBefore(identity.expiresAt())) throw denied();
            // Hold the Account lock through delivery: governance cannot commit between check and send.
            delivery.run();
        });
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("Socket authorization expired or revoked"); }
}
