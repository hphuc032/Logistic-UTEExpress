package com.uteexpress.security.service;

import java.util.Optional;

public interface CurrentAccountIdProvider {
    Optional<Long> currentAccountId();

    default Long requireCurrentAccountId() {
        return currentAccountId()
                .orElseThrow(() -> new IllegalStateException("No authenticated account is available"));
    }
}
