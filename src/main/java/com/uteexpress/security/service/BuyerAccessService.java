package com.uteexpress.security.service;

import com.uteexpress.security.CurrentUserProvider;
import com.uteexpress.security.RoleCode;
import org.springframework.stereotype.Service;

/** Narrow role check for optional buyer features on public pages. */
@Service
public class BuyerAccessService {
    private final CurrentUserProvider users;

    public BuyerAccessService(CurrentUserProvider users) { this.users = users; }

    public boolean currentUserCanBuy() {
        return users.currentUser().map(user -> user.hasRole(RoleCode.USER)
                || user.hasRole(RoleCode.VENDOR)).orElse(false);
    }
}
