package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.*;
import com.uteexpress.catalog.entity.Product;
import com.uteexpress.catalog.entity.ProductStatus;
import com.uteexpress.catalog.repository.ProductRepository;
import com.uteexpress.common.exception.*;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.PromotionManagementService;
import com.uteexpress.shop.service.ShopAvailabilityService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Catalog owns Product authority/sentinel; Promotion keeps one shared schedule writer. */
@Service @Validated
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
@Transactional
public class AdminProductPromotionService {
    private final ObjectProvider<ProductRepository> products;
    private final ObjectProvider<EntityManager> entities;
    private final ShopAvailabilityService shops;
    private final RoleGovernanceService actors;
    private final PromotionManagementService promotions;
    private final AuditLogService audit;
    public AdminProductPromotionService(ObjectProvider<ProductRepository> products, ObjectProvider<EntityManager> entities,
            ShopAvailabilityService shops, RoleGovernanceService actors, PromotionManagementService promotions, AuditLogService audit) {
        this.products=products; this.entities=entities; this.shops=shops; this.actors=actors; this.promotions=promotions; this.audit=audit;
    }
    public PromotionProductPage products(String query, int page) {
        actor();
        String q=query == null ? "" : query.strip();
        if (q.length()>100 || page<0 || page>10000) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        var rows=products.getObject().searchForModeration(q, PageRequest.of(page,20,Sort.by(Sort.Direction.DESC,"id")));
        return new PromotionProductPage(rows.getContent().stream().map(AdminProductPromotionService::option).toList(),q,page,rows.hasPrevious(),rows.hasNext());
    }
    public PromotionProductOption product(Long productId) { actor(); return option(lock(productId,false)); }
    public List<PromotionView> list(Long productId) { actor(); lock(productId,false); return promotions.listForAdministrator(productId); }
    public PromotionView get(Long productId,Long id) { actor(); lock(productId,false); return promotions.getForAdministrator(productId,id); }
    public Long create(Long productId,@NotNull @Valid AdminPromotionForm form) {
        Long actor=actor(); lock(productId,true);
        Long id=promotions.createForAdministrator(productId,form);
        record(actor,"PRODUCT_PROMOTION_CREATED",promotions.getForAdministrator(productId,id),Map.of()); return id;
    }
    public void update(Long productId,Long id,@NotNull @Valid AdminPromotionUpdateForm form) {
        Long actor=actor(); lock(productId,true);
        var before=promotions.getForAdministrator(productId,id);
        promotions.updateForAdministrator(productId,id,form);
        record(actor,"PRODUCT_PROMOTION_UPDATED",promotions.getForAdministrator(productId,id),state(before));
    }
    public void disable(Long productId,Long id,Long version) {
        Long actor=actor(); lock(productId,false);
        var before=promotions.getForAdministrator(productId,id);
        promotions.disableForAdministrator(productId,id,version);
        var after=promotions.getForAdministrator(productId,id);
        if (!state(before).equals(state(after))) record(actor,"PRODUCT_PROMOTION_DISABLED",after,state(before));
    }
    private Product lock(Long id,boolean requireAvailable) {
        if (id==null || id<=0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        var product=products.getObject().findByIdForUpdate(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entities.getObject().refresh(product,LockModeType.PESSIMISTIC_WRITE);
        shops.lockForPromotionManagement(product.getShopId(),requireAvailable);
        if (requireAvailable && product.getStatus()!=ProductStatus.ACTIVE) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        return product;
    }
    private Long actor() { return actors.requireCurrentActiveAdministratorForUpdate(); }
    private static PromotionProductOption option(Product p) { return new PromotionProductOption(p.getId(),p.getName(),p.getShopId(),p.getStatus().name()); }
    private static Map<String,String> state(PromotionView p) { return Map.of("active",Boolean.toString(p.active()),"version",p.version().toString(),"relatedId",p.productId().toString()); }
    private void record(Long actor,String action,PromotionView promotion,Map<String,String> before) {
        audit.append(new AuditEntry(actor,action,"PROMOTION",promotion.id(),before,state(promotion),"ADMIN_PRODUCT_PROMOTION"));
    }
}
