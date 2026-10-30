package com.uteexpress.order.controller;

import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.service.OrderPlacementService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/checkout/view/place-order")
public class PlaceOrderPageController {
    private final OrderPlacementService placement;
    public PlaceOrderPageController(OrderPlacementService placement) { this.placement = placement; }

    @InitBinder("placeOrderForm")
    void fields(WebDataBinder binder) {
        binder.setAllowedFields("checkoutKey", "items[*].productId", "items[*].quantity", "addressId",
                "shippingProviderId", "shippingServiceCode", "paymentMethod", "voucherCode");
    }

    @PostMapping
    String place(@Valid @ModelAttribute("placeOrderForm") CheckoutRequest request, BindingResult binding,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        redirect.addFlashAttribute("placedOrder", placement.placeOrder(request));
        return "redirect:/user/checkout/view";
    }

    @ExceptionHandler(ApplicationException.class)
    String error(ApplicationException exception, RedirectAttributes redirect) {
        if (exception.errorCode() == ErrorCode.UNAUTHENTICATED || exception.errorCode() == ErrorCode.ACCESS_DENIED) {
            throw exception;
        }
        redirect.addFlashAttribute("errorMessage", exception.detail() != null
                ? exception.publicMessage()
                : "Không thể đặt hàng với lựa chọn hiện tại. Vui lòng kiểm tra giỏ hàng, địa chỉ và xem lại báo giá.");
        return "redirect:/user/checkout/view";
    }
}
