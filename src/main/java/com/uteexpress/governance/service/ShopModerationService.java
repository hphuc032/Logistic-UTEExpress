package com.uteexpress.governance.service;

import com.uteexpress.governance.dto.ModerationView;
import org.springframework.data.domain.Page;

/** Shop-owned implementation; suspension does not change account roles or tokens. */
public interface ShopModerationService {
    Page<ModerationView> search(String query, int page);
    ModerationView change(Long id, Long version, boolean restrict, String reason);
}
