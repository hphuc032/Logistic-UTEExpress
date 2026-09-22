package com.uteexpress.security.authentication;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class UteExpressPrincipal implements UserDetails, CredentialsContainer {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String subject;
    private final String displayUsername;
    private final long tokenVersion;
    private final List<GrantedAuthority> authorities;
    private final boolean active;
    private String passwordHash;

    public UteExpressPrincipal(Long userId, String displayUsername, String passwordHash,
            long tokenVersion, Collection<? extends GrantedAuthority> authorities, boolean active) {
        this.userId = userId;
        this.subject = "uteexpress:user:" + userId;
        this.displayUsername = displayUsername;
        this.passwordHash = passwordHash;
        this.tokenVersion = tokenVersion;
        this.authorities = List.copyOf(authorities);
        this.active = active;
    }

    public Long userId() { return userId; }
    public String subject() { return subject; }
    public String displayUsername() { return displayUsername; }
    public long tokenVersion() { return tokenVersion; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return subject; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }

    @Override
    public void eraseCredentials() { passwordHash = null; }
}
