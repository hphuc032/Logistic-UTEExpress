package com.uteexpress.governance.service;

import com.uteexpress.governance.dto.ModerationView;
import org.springframework.data.domain.Page;

/** Catalog-owned implementation; Ops never accesses Catalog persistence directly. */
public interface ProductModerationService {
    Page<ModerationView> search(String query, int page);
    ModerationView change(Long id, Long version, boolean restrict, String reason);
}
