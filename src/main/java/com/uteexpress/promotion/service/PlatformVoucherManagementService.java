package com.uteexpress.promotion.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.governance.service.RoleGovernanceService;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.entity.Voucher;
import com.uteexpress.promotion.entity.VoucherUsage;
import com.uteexpress.promotion.repository.VoucherRepository;
import com.uteexpress.promotion.repository.VoucherUsageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Admin management only. Existing Voucher/VoucherService remain pricing and usage authorities. */
@Service @Validated
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
public class PlatformVoucherManagementService {
    private final ObjectProvider<VoucherRepository> vouchers;
    private final ObjectProvider<VoucherUsageRepository> usages;
    private final ObjectProvider<EntityManager> entities;
    private final RoleGovernanceService roles;
    private final AuditLogService audit;
    private final Clock clock;

    public PlatformVoucherManagementService(ObjectProvider<VoucherRepository> vouchers,
            ObjectProvider<VoucherUsageRepository> usages, ObjectProvider<EntityManager> entities,
            RoleGovernanceService roles, AuditLogService audit, Clock clock) {
        this.vouchers = vouchers; this.usages = usages; this.entities = entities;
        this.roles = roles; this.audit = audit; this.clock = clock;
    }

    // Even reads serialize account eligibility with revocation; PostgreSQL read-only transactions cannot take this lock.
    @Transactional public void requireAdministrator() { actor(); }
    @Transactional public List<PlatformVoucherView> list() {
        actor();
        return vouchers.getObject().findAllByScopeAndShopIdIsNullOrderByIdDesc(VoucherScope.PLATFORM)
                .stream().map(this::view).toList();
    }
    @Transactional public PlatformVoucherView get(Long id) {
        actor(); requireId(id);
        return view(vouchers.getObject().findByIdAndScopeAndShopIdIsNull(id, VoucherScope.PLATFORM)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND)));
    }
    @Transactional public Long create(@NotNull @Valid PlatformVoucherForm form) {
        Long actor = actor();
        String code = VoucherCode.normalize(form.getCode());
        if (vouchers.getObject().existsByCode(code)) throw conflict();
        var voucher = persist(new Voucher(code, VoucherScope.PLATFORM, null, form.getType(), form.getValue(),
                form.getMaxDiscount(), form.getMinSubtotal(), instant(form.getStartsAt()), instant(form.getEndsAt()),
                form.getTotalLimit(), form.getPerUserLimit(), form.isActive(), actor, now()));
        record(actor, "PLATFORM_VOUCHER_CREATED", voucher, Map.of());
        return voucher.getId();
    }
    @Transactional public void update(Long id, @NotNull @Valid PlatformVoucherUpdateForm form) {
        Long actor = actor();
        var voucher = lock(id, form.getVersion());
        var before = state(voucher);
        String code = VoucherCode.normalize(form.getCode());
        if (vouchers.getObject().existsByCodeAndIdNot(code, id)) throw conflict();
        voucher.update(code, form.getType(), form.getValue(), form.getMaxDiscount(), form.getMinSubtotal(),
                instant(form.getStartsAt()), instant(form.getEndsAt()), form.getTotalLimit(), form.getPerUserLimit(),
                form.isActive(), usages.getObject().existsByVoucherId(id), now());
        persist(voucher);
        record(actor, "PLATFORM_VOUCHER_UPDATED", voucher, before);
    }
    @Transactional public void disable(Long id, @NotNull @Valid VoucherVersionForm form) {
        Long actor = actor();
        var voucher = lock(id, form.getVersion());
        var before = state(voucher);
        if (!voucher.isActive()) return; // Version is still checked before an idempotent no-op.
        voucher.disable(now()); persist(voucher);
        record(actor, "PLATFORM_VOUCHER_DISABLED", voucher, before);
    }
    /** Account -> Voucher. Never acquire Product, Order or Usage locks after the Voucher lock. */
    private Long actor() {
        return roles.requireCurrentActiveAdministratorForUpdate();
    }
    private Voucher lock(Long id, Long expected) {
        requireId(id);
        var voucher = vouchers.getObject().findPlatformForUpdate(id, VoucherScope.PLATFORM)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entities.getObject().refresh(voucher, LockModeType.PESSIMISTIC_WRITE);
        if (voucher.getScope() != VoucherScope.PLATFORM || voucher.getShopId() != null)
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!Objects.equals(expected, voucher.getVersion())) throw conflict();
        return voucher;
    }
    private Voucher persist(Voucher voucher) {
        try { return vouchers.getObject().saveAndFlush(voucher); }
        catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException error) { throw conflict(); }
    }
    private void record(Long actor, String action, Voucher voucher, Map<String, String> before) {
        audit.append(new AuditEntry(actor, action, "VOUCHER", voucher.getId(), before, state(voucher), "PLATFORM_VOUCHER_MANAGEMENT"));
    }
    private static Map<String, String> state(Voucher voucher) {
        return Map.of("active", Boolean.toString(voucher.isActive()), "version", voucher.getVersion().toString());
    }
    private PlatformVoucherView view(Voucher v) {
        return new PlatformVoucherView(v.getId(), v.getCode(), v.getType(), v.getValue(), v.getMaxDiscount(), v.getMinSubtotal(),
                LocalDateTime.ofInstant(v.getStartsAt(), VendorVoucherService.FORM_ZONE),
                LocalDateTime.ofInstant(v.getEndsAt(), VendorVoucherService.FORM_ZONE), v.getTotalLimit(), v.getPerUserLimit(),
                usages.getObject().countByVoucherIdAndStatus(v.getId(), VoucherUsage.Status.REDEEMED),
                usages.getObject().existsByVoucherId(v.getId()), v.isActive(), v.getVersion());
    }
    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
    private static Instant instant(LocalDateTime time) { return time.atZone(VendorVoucherService.FORM_ZONE).toInstant(); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
