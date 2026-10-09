package com.uteexpress.promotion.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.entity.Promotion;
import com.uteexpress.promotion.repository.PromotionRepository;
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
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

/** Internal persistence boundary. Catalog authorizes ownership and holds Product -> Shop locks before writes. */
@Service @Validated
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class PromotionManagementService {
    public static final ZoneId FORM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PromotionRepository promotions;
    private final EntityManager entityManager;
    private final Clock clock;
    public PromotionManagementService(PromotionRepository promotions, EntityManager entityManager, Clock clock) {
        this.promotions = promotions; this.entityManager = entityManager; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<VendorPromotionView> list(Long productId) {
        Instant at = now();
        return promotions.findAllByProductIdOrderByStartsAtDescIdDesc(productId).stream().map(p -> view(p, at)).toList();
    }
    @Transactional(readOnly = true)
    public VendorPromotionView get(Long productId, Long id) {
        return view(promotions.findByIdAndProductId(id, productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND)), now());
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Long create(Long productId, Long creator, @NotNull @Valid VendorPromotionForm form) {
        var promotion = new Promotion(productId, creator, form.getName(), form.getDiscountPercent(),
                instant(form.getStartsAt()), instant(form.getEndsAt()), form.isActive(), now());
        overlap(promotion, null);
        return persist(promotion).getId();
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Long productId, Long id, @NotNull @Valid VendorPromotionUpdateForm form) {
        var promotion = locked(productId, id);
        if (!Objects.equals(promotion.getVersion(), form.getVersion())) conflict();
        // Validate before mutating a managed entity: JPQL queries may trigger AUTO flush.
        if (form.isActive() && promotions.countOverlaps(productId, instant(form.getStartsAt()),
                instant(form.getEndsAt()), id) > 0) conflict();
        promotion.update(form.getName(), form.getDiscountPercent(), instant(form.getStartsAt()),
                instant(form.getEndsAt()), form.isActive(), now());
        persist(promotion);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void disable(Long productId, Long id, Long version) {
        var promotion = locked(productId, id);
        if (version == null || version < 0) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        if (!Objects.equals(promotion.getVersion(), version)) conflict();
        promotion.disable(now()); persist(promotion);
    }
    private Promotion locked(Long productId, Long id) {
        var promotion = promotions.findOwnedForUpdate(id, productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entityManager.refresh(promotion, LockModeType.PESSIMISTIC_WRITE);
        return promotion;
    }
    private void overlap(Promotion p, Long excludeId) {
        if (p.isActive() && promotions.countOverlaps(p.getProductId(), p.getStartsAt(), p.getEndsAt(), excludeId) > 0) conflict();
    }
    private Promotion persist(Promotion p) {
        try { return promotions.saveAndFlush(p); }
        catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException error) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }
    private VendorPromotionView view(Promotion p, Instant at) {
        String status = !p.isActive() ? "Đã tắt" : at.isBefore(p.getStartsAt()) ? "Sắp diễn ra"
                : at.isBefore(p.getEndsAt()) ? "Đang chạy" : "Đã kết thúc";
        return new VendorPromotionView(p.getId(), p.getProductId(), p.getName(), p.getDiscountPercent(),
                LocalDateTime.ofInstant(p.getStartsAt(), FORM_ZONE), LocalDateTime.ofInstant(p.getEndsAt(), FORM_ZONE),
                p.isActive(), p.getVersion(), status);
    }
    private static Instant instant(LocalDateTime at) { return at.atZone(FORM_ZONE).toInstant().truncatedTo(ChronoUnit.MICROS); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static void conflict() { throw new ApplicationException(ErrorCode.CONFLICT); }
}
