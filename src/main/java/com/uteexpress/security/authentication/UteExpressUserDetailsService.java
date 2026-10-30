package com.uteexpress.security.authentication;

import com.uteexpress.identity.service.IdentityAuthenticationService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UteExpressUserDetailsService implements UserDetailsService {
    private final IdentityAuthenticationService identities;
    private final UteExpressPrincipalFactory principalFactory;

    public UteExpressUserDetailsService(IdentityAuthenticationService identities,
            UteExpressPrincipalFactory principalFactory) {
        this.identities = identities;
        this.principalFactory = principalFactory;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        return identities.findByLoginIdentifier(identifier)
                .filter(account -> account.active())
                .map(account -> principalFactory.create(account, true))
                .orElseThrow(() -> new UsernameNotFoundException("Authentication failed"));
    }
}
