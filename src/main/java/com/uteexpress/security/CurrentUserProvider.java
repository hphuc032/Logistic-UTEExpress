package com.uteexpress.security;

import java.util.Optional;

/**
 * Supplies the authenticated subject to business services.
 * Business ownership must never be inferred from request parameters or form fields.
 */
public interface CurrentUserProvider {
    boolean isAuthenticated();

    Optional<CurrentUser> currentUser();

    default CurrentUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new IllegalStateException("No authenticated user is available"));
    }
}
