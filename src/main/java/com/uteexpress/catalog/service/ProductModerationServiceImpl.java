package com.uteexpress.catalog.service;

import com.uteexpress.catalog.entity.Product;
import com.uteexpress.catalog.entity.ProductStatus;
import com.uteexpress.catalog.repository.ProductRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.dto.ModerationView;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.governance.service.ProductModerationService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
public class ProductModerationServiceImpl implements ProductModerationService {
    private final ProductRepository repository;
    private final AuditLogService audit;
    private final CurrentAccountIdProvider accounts;
    private final Clock clock;

    public ProductModerationServiceImpl(ProductRepository repository, AuditLogService audit,
            CurrentAccountIdProvider accounts, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public Page<ModerationView> search(String query, int page) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > 120 || page < 0 || page > 100000)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return repository.searchForModeration(normalized,
                PageRequest.of(page, 20, Sort.by("id").descending())).map(ProductModerationServiceImpl::view);
    }

    @Override @Transactional
    public ModerationView change(Long id, Long version, boolean restrict, String reason) {
        Long actor = accounts.currentAccountId().filter(value -> value > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        String normalized = reason == null ? "" : reason.trim();
        if (id == null || id <= 0 || version == null || version < 0
                || normalized.isEmpty() || normalized.length() > 1000
                || normalized.codePoints().anyMatch(Character::isISOControl))
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        Product item = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(item.getVersion(), version)
                || item.getStatus() != (restrict ? ProductStatus.ACTIVE : ProductStatus.MODERATED))
            throw new ApplicationException(ErrorCode.CONFLICT);
        Map<String, String> before = Map.of("status", item.getStatus().name(), "version", item.getVersion().toString());
        item.moderate(restrict, normalized, Instant.now(clock));
        repository.saveAndFlush(item);
        audit.append(new AuditEntry(actor, restrict ? "PRODUCT_MODERATED" : "PRODUCT_RESTORED",
                "PRODUCT", id, before,
                Map.of("status", item.getStatus().name(), "version", item.getVersion().toString()), "OPS_MODERATION"));
        return view(item);
    }

    private static ModerationView view(Product item) {
        return new ModerationView(item.getId(), item.getName(), item.getStatus().name(), item.getVersion(), item.getModerationReason());
    }
}
