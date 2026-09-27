package com.uteexpress.shop.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.identity.service.VendorRoleGrantService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.dto.ShopApprovalView;
import com.uteexpress.shop.entity.Shop;
import com.uteexpress.shop.entity.ShopStatus;
import com.uteexpress.shop.repository.ShopRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
public class ShopApprovalService {
    private final ShopRepository shops;
    private final VendorRoleGrantService roles;
    private final AuditLogService audit;
    private final CurrentAccountIdProvider accounts;
    private final Clock clock;

    public ShopApprovalService(ShopRepository shops, VendorRoleGrantService roles,
            AuditLogService audit, CurrentAccountIdProvider accounts, Clock clock) {
        this.shops = shops;
        this.roles = roles;
        this.audit = audit;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ShopApprovalView> pending(int page) {
        if (page < 0 || page > 100000) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return shops.findByStatus(ShopStatus.PENDING,
                PageRequest.of(page, 20, Sort.by("createdAt").ascending().and(Sort.by("id"))))
                .map(ShopApprovalService::view);
    }

    @Transactional(readOnly = true)
    public ShopApprovalView get(Long id) {
        requireId(id);
        return view(shops.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional
    public ShopApprovalView approve(Long id, Long expectedVersion) {
        Long actorId = actor();
        Shop shop = pendingForUpdate(id, expectedVersion);
        Map<String, String> before = state(shop);
        shop.approve(Instant.now(clock));
        Shop saved = save(shop);
        roles.grantToActiveOwner(shop.getOwnerId());
        audit.append(new AuditEntry(actorId, "SHOP_APPROVED", "SHOP", id,
                before, state(saved), "SHOP_APPROVAL"));
        return view(saved);
    }

    @Transactional
    public ShopApprovalView reject(Long id, Long expectedVersion, String reason) {
        Long actorId = actor();
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty() || normalized.length() > 1000)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        Shop shop = pendingForUpdate(id, expectedVersion);
        Map<String, String> before = state(shop);
        shop.reject(normalized, Instant.now(clock));
        Shop saved = save(shop);
        audit.append(new AuditEntry(actorId, "SHOP_REJECTED", "SHOP", id,
                before, state(saved), "SHOP_REJECTION"));
        return view(saved);
    }

    private Shop pendingForUpdate(Long id, Long expectedVersion) {
        requireId(id);
        if (expectedVersion == null || expectedVersion < 0)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        Shop shop = shops.findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (shop.getStatus() != ShopStatus.PENDING || !Objects.equals(shop.getVersion(), expectedVersion))
            throw new ApplicationException(ErrorCode.CONFLICT);
        return shop;
    }

    private Shop save(Shop shop) {
        try { return shops.saveAndFlush(shop); }
        catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException conflict) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    private Long actor() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
    }

    private static Map<String, String> state(Shop shop) {
        return Map.of("status", shop.getStatus().name(), "version", shop.getVersion().toString());
    }

    private static ShopApprovalView view(Shop shop) {
        return new ShopApprovalView(shop.getId(), shop.getOwnerId(), shop.getName(), shop.getSlug(),
                shop.getDescription(), shop.getPickupAddress(), shop.getStatus().name(),
                shop.getRejectionReason(), shop.getCreatedAt(), shop.getVersion());
    }
}
