package com.uteexpress.shop.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.shop.entity.ShopStatus;
import com.uteexpress.shop.repository.ShopRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Shared checkout boundary for the same shop row lock used by shop moderation. */
@Service
public class ShopAvailabilityService {
    private final ObjectProvider<ShopRepository> shops;

    public ShopAvailabilityService(ObjectProvider<ShopRepository> shops) { this.shops = shops; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireApprovedForCheckout(Long shopId) {
        if (shopId == null || shopId <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        var shop = shops.getObject().findByIdForUpdate(shopId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (shop.getStatus() != ShopStatus.APPROVED) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
