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
    private final ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc;

    public SocketIdentityService(JwtDecoder decoder, JwtProperties properties,
            IdentityAuthenticationService identities, AccountIdentityService accounts,
            ObjectProvider<PlatformTransactionManager> managers, Clock clock,
            ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc) {
        this.decoder = decoder; this.properties = properties; this.identities = identities;
        this.accounts = accounts; this.managers = managers; this.clock = clock; this.jdbc = jdbc;
    }

    public String cookieName() { return properties.cookieName(); }

    public SocketIdentity authenticate(String token) {
        var jwt = decoder.decode(token); // existing signature/issuer/audience/claims validators
        Object subject = jwt.getClaims().get("sub");
        Object version = jwt.getClaims().get("tokenVersion");
        // Validate exact types, canonical decimal syntax and signed-long range before parsing.
        if (!(subject instanceof String text) || !text.matches("uteexpress:user:[1-9][0-9]{0,18}")) throw denied();
        String digits = text.substring("uteexpress:user:".length());
        if (digits.length() == 19 && digits.compareTo("9223372036854775807") > 0) throw denied();
        if (!(version instanceof Byte || version instanceof Short || version instanceof Integer || version instanceof Long
                || version instanceof java.math.BigInteger)) throw denied();
        var exactVersion = new java.math.BigInteger(version.toString());
        if (exactVersion.signum() < 0 || exactVersion.bitLength() > 63) throw denied();
        long accountId = Long.parseLong(digits);
        var identity = new SocketIdentity(accountId, exactVersion.longValueExact(),
                identities.findByUserId(accountId).orElseThrow(SocketIdentityService::denied).roleCodes(), jwt.getExpiresAt());
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
            // Transaction timeout alone need not bound time spent inside a JDBC lock wait.
            // LOCAL settings end with this Account-only transaction, never leak through the pool.
            jdbc.getObject().execute("SET LOCAL lock_timeout = '2000ms'");
            jdbc.getObject().execute("SET LOCAL statement_timeout = '4000ms'");
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
