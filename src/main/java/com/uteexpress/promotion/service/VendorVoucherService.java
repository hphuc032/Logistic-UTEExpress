package com.uteexpress.promotion.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.entity.*;
import com.uteexpress.promotion.repository.*;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.dto.VendorShopData;
import com.uteexpress.shop.service.VendorShopQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class VendorVoucherService {
    public static final ZoneId FORM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final VoucherRepository vouchers;
    private final VoucherUsageRepository usages;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final VendorShopQueryService shops;
    private final EntityManager entityManager;
    private final Clock clock;

    public VendorVoucherService(VoucherRepository vouchers, VoucherUsageRepository usages,
            CurrentAccountIdProvider accounts, AccountIdentityService identities,
            VendorShopQueryService shops, EntityManager entityManager, Clock clock) {
        this.vouchers = vouchers;
        this.usages = usages;
        this.accounts = accounts;
        this.identities = identities;
        this.shops = shops;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VendorShopData currentShop() { return shops.requireApprovedOwnedShop(vendorId()); }

    @Transactional(readOnly = true)
    public List<VendorVoucherView> list() {
        return vouchers.findAllByShopIdAndScopeOrderByIdDesc(currentShop().shopId(), VoucherScope.SHOP)
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public VendorVoucherView get(Long id) {
        requireId(id);
        return view(vouchers.findByIdAndShopIdAndScope(id, currentShop().shopId(), VoucherScope.SHOP)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional
    public Long create(@NotNull @Valid VendorVoucherForm form) {
        Long vendor = vendorId();
        Long shop = lockShop(vendor);
        String code = VoucherCode.normalize(form.getCode());
        if (vouchers.existsByCode(code)) throw new ApplicationException(ErrorCode.CONFLICT);
        return persist(new Voucher(code, VoucherScope.SHOP, shop, form.getType(), form.getValue(),
                form.getMaxDiscount(), form.getMinSubtotal(), instant(form.getStartsAt()), instant(form.getEndsAt()),
                form.getTotalLimit(), form.getPerUserLimit(), form.isActive(), vendor, now())).getId();
    }

    @Transactional
    public void update(Long id, @NotNull @Valid VendorVoucherUpdateForm form) {
        var voucher = lockOwned(id);
        if (!Objects.equals(voucher.getVersion(), form.getVersion())) throw new ApplicationException(ErrorCode.CONFLICT);
        String code = VoucherCode.normalize(form.getCode());
        if (vouchers.existsByCodeAndIdNot(code, id)) throw new ApplicationException(ErrorCode.CONFLICT);
        voucher.update(code, form.getType(), form.getValue(), form.getMaxDiscount(), form.getMinSubtotal(),
                instant(form.getStartsAt()), instant(form.getEndsAt()), form.getTotalLimit(), form.getPerUserLimit(),
                form.isActive(), usages.existsByVoucherId(id), now());
        persist(voucher);
    }

    @Transactional
    public void disable(Long id) {
        var voucher = lockOwned(id);
        voucher.disable(now());
        persist(voucher);
    }

    private Voucher lockOwned(Long id) {
        requireId(id);
        Long shop = lockShop(vendorId());
        var voucher = vouchers.findOwnedForUpdate(id, shop, VoucherScope.SHOP)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entityManager.refresh(voucher, LockModeType.PESSIMISTIC_WRITE);
        if (voucher.getScope() != VoucherScope.SHOP || !shop.equals(voucher.getShopId())) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return voucher;
    }

    /** Account -> Shop -> Voucher, compatible with checkout/cancellation. No Order/Product/Usage locks. */
    private Long lockShop(Long vendor) {
        identities.requireActiveAccountForUpdate(vendor);
        return shops.requireApprovedOwnedShopForUpdate(vendor).shopId();
    }

    private Voucher persist(Voucher voucher) {
        try { return vouchers.saveAndFlush(voucher); }
        catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException conflict) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    private VendorVoucherView view(Voucher v) {
        return new VendorVoucherView(v.getId(), v.getCode(), v.getType(), v.getValue(), v.getMaxDiscount(),
                v.getMinSubtotal(), LocalDateTime.ofInstant(v.getStartsAt(), FORM_ZONE),
                LocalDateTime.ofInstant(v.getEndsAt(), FORM_ZONE), v.getTotalLimit(), v.getPerUserLimit(),
                usages.countByVoucherIdAndStatus(v.getId(), VoucherUsage.Status.REDEEMED),
                usages.existsByVoucherId(v.getId()), v.isActive(), v.getVersion());
    }
    private Long vendorId() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }
    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    private static Instant instant(LocalDateTime time) { return time.atZone(FORM_ZONE).toInstant(); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
