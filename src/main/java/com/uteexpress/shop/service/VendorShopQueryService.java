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

    public VendorShopQueryService(ShopRepository shops) {
        this.shops = shops;
    }

    @Transactional(readOnly = true)
    public VendorShopData requireApprovedOwnedShop(Long ownerId) {
        if (ownerId == null || ownerId <= 0) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return shops.findByOwnerId(ownerId)
                .filter(shop -> shop.getStatus() == ShopStatus.APPROVED)
                .map(shop -> new VendorShopData(shop.getId(), shop.getName()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
    }
}
