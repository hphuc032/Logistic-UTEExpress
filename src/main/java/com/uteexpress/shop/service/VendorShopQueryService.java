package com.uteexpress.shop.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.shop.dto.VendorShopData;
import com.uteexpress.shop.entity.ShopStatus;
import com.uteexpress.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorShopQueryService {
    private final ShopRepository shops;
    private final jakarta.persistence.EntityManager entityManager;

    public VendorShopQueryService(ShopRepository shops, jakarta.persistence.EntityManager entityManager) {
        this.shops = shops;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public VendorShopData requireApprovedOwnedShop(Long ownerId) {
        if (ownerId == null || ownerId <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return shops.findByOwnerId(ownerId)
                .filter(shop -> shop.getStatus() == ShopStatus.APPROVED)
                .map(shop -> new VendorShopData(shop.getId(), shop.getName()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
    }

    /** ORD-03 guard. Call after inventory locks when restoring stock, matching checkout ordering. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public VendorShopData requireApprovedOwnedShopForUpdate(Long ownerId) {
        var owned = requireApprovedOwnedShop(ownerId);
        var shop = shops.findByIdForUpdate(owned.shopId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
        entityManager.refresh(shop, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!shop.getOwnerId().equals(ownerId) || shop.getStatus() != ShopStatus.APPROVED) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return new VendorShopData(shop.getId(), shop.getName());
    }
}
