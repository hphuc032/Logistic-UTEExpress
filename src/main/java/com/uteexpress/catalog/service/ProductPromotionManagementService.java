package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductView;
import com.uteexpress.catalog.entity.Product;
import com.uteexpress.catalog.repository.ProductRepository;
import com.uteexpress.common.exception.*;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.PromotionManagementService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.service.VendorShopQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog owns Product authority. Dependencies remain Catalog -> Promotion, never the reverse. */
@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class ProductPromotionManagementService {
    private final ProductService productViews;
    private final ProductRepository products;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final VendorShopQueryService shops;
    private final PromotionManagementService promotions;
    private final EntityManager entityManager;
    public ProductPromotionManagementService(ProductService productViews, ProductRepository products,
            CurrentAccountIdProvider accounts, AccountIdentityService identities, VendorShopQueryService shops,
            PromotionManagementService promotions, EntityManager entityManager) {
        this.productViews = productViews; this.products = products; this.accounts = accounts;
        this.identities = identities; this.shops = shops; this.promotions = promotions; this.entityManager = entityManager;
    }
    @Transactional(readOnly = true)
    public ProductView product(Long productId) { return productViews.getCurrentShopProduct(productId); }
    @Transactional(readOnly = true)
    public List<VendorPromotionView> list(Long productId) { product(productId); return promotions.list(productId); }
    @Transactional(readOnly = true)
    public VendorPromotionView get(Long productId, Long id) { product(productId); return promotions.get(productId, id); }
    @Transactional
    public Long create(Long productId, VendorPromotionForm form) {
        Long vendor = vendorId(); lockOwned(productId, vendor); return promotions.create(productId, vendor, form);
    }
    @Transactional
    public void update(Long productId, Long id, VendorPromotionUpdateForm form) {
        lockOwned(productId, vendorId()); promotions.update(productId, id, form);
    }
    @Transactional
    public void disable(Long productId, Long id, Long version) {
        lockOwned(productId, vendorId()); promotions.disable(productId, id, version);
    }
    private void lockOwned(Long productId, Long vendor) {
        if (productId == null || productId <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        identities.requireActiveAccountForUpdate(vendor);
        Long shopId = shops.requireApprovedOwnedShop(vendor).shopId(); // no Shop lock yet
        Product product = products.findByIdAndShopIdForUpdate(productId, shopId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        if (!product.getShopId().equals(shopId)) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        shops.requireApprovedOwnedShopForUpdate(vendor); // Account -> Product -> Shop -> Promotion
    }
    private Long vendorId() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }
}
