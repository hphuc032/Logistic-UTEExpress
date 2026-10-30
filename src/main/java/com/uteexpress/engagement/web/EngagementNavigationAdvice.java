package com.uteexpress.engagement.web;

import com.uteexpress.security.service.BuyerAccessService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes the same buyer-role rule used by engagement services to shared templates. */
@ControllerAdvice
public class EngagementNavigationAdvice {
    private final BuyerAccessService buyers;

    public EngagementNavigationAdvice(BuyerAccessService buyers) {
        this.buyers = buyers;
    }

    @ModelAttribute("buyerFeaturesAvailable")
    public boolean buyerFeaturesAvailable() {
        return buyers.currentUserCanBuy();
    }
}
