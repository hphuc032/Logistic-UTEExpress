package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.*;
import com.uteexpress.governance.entity.Category;
import com.uteexpress.governance.repository.CategoryRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.Map;
import java.util.Objects;

@Service
@Validated
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
public class CategoryService {
    private final CategoryRepository categories;
    private final AuditLogService audit;
    private final CurrentAccountIdProvider accounts;

    public CategoryService(CategoryRepository categories, AuditLogService audit, CurrentAccountIdProvider accounts) {
        this.categories = categories;
        this.audit = audit;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Page<CategoryView> list(int page) {
        if (page < 0 || page > 100000) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return categories.findAll(PageRequest.of(page, 20, Sort.by("id").descending())).map(CategoryService::view);
    }

    @Transactional(readOnly = true)
    public CategoryView get(Long id) { return view(require(id)); }

    @Transactional
    public CategoryView create(@NotNull @Valid CategoryRequest request) {
        Long actor = actor();
        Category category = persist(new Category(request.name(), request.slug()));
        audit.append(new AuditEntry(actor, "CATEGORY_CREATED", "CATEGORY", category.getId(),
                Map.of(), state(category), "CATEGORY_CONFIGURATION"));
        return view(category);
    }

    @Transactional
    public CategoryView update(Long id, @NotNull @Valid CategoryRequest request) {
        Long actor = actor();
        Category category = require(id);
        checkVersion(category, request.version());
        Map<String, String> before = state(category);
        category.rename(request.name(), request.slug());
        persist(category);
        audit.append(new AuditEntry(actor, "CATEGORY_UPDATED", "CATEGORY", id,
                before, state(category), "CATEGORY_CONFIGURATION"));
        return view(category);
    }

    @Transactional
    public void setActive(Long id, Long expectedVersion, boolean active) {
        Long actor = actor();
        Category category = require(id);
        checkVersion(category, expectedVersion);
        if (category.isActive() == active) return;
        Map<String, String> before = state(category);
        category.changeActive(active);
        persist(category);
        audit.append(new AuditEntry(actor, active ? "CATEGORY_ENABLED" : "CATEGORY_DISABLED", "CATEGORY", id,
                before, state(category), "CATEGORY_CONFIGURATION"));
    }

    private Long actor() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }
    private Category require(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return categories.findById(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private static void checkVersion(Category category, Long version) {
        if (version == null || version < 0) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        if (!Objects.equals(category.getVersion(), version)) throw new ApplicationException(ErrorCode.CONFLICT);
    }
    private Category persist(Category category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException conflict) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }
    private static Map<String, String> state(Category category) {
        return Map.of("active", Boolean.toString(category.isActive()), "version", category.getVersion().toString());
    }
    private static CategoryView view(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getSlug(),
                category.isActive(), category.getVersion());
    }
}
