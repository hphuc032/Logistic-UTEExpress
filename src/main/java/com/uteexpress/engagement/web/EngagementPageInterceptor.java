package com.uteexpress.engagement.web;

import com.uteexpress.catalog.dto.ProductDetailView;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.engagement.service.EngagementService;
import com.uteexpress.security.service.BuyerAccessService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class EngagementPageInterceptor implements HandlerInterceptor {
    private final EngagementService engagement;
    private final CurrentAccountIdProvider accountIds;
    private final BuyerAccessService buyers;

    public EngagementPageInterceptor(EngagementService engagement, CurrentAccountIdProvider accountIds,
            BuyerAccessService buyers) {
        this.engagement = engagement;
        this.accountIds = accountIds;
        this.buyers = buyers;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler, ModelAndView view) {
        if (view == null || !"GET".equals(request.getMethod()) || !"products/detail".equals(view.getViewName())) return;
        Object candidate = view.getModel().get("product");
        if (!(candidate instanceof ProductDetailView product) || !eligibleUser()) return;
        try {
            engagement.recordView(product.id());
            view.addObject("isFavorite", engagement.isFavorite(product.id()));
        } catch (ApplicationException exception) {
            // The product can be moderated between the public read and view recording.
            if (exception.errorCode() != ErrorCode.RESOURCE_NOT_FOUND) throw exception;
        }
    }

    private boolean eligibleUser() {
        if (accountIds.currentAccountId().filter(id -> id > 0).isEmpty()) return false;
        return buyers.currentUserCanBuy();
    }
}
