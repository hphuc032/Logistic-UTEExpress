package com.uteexpress.checkout.controller;

import com.uteexpress.checkout.dto.QuoteRequest;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/checkout/view")
public class CheckoutPageController {
    private final CheckoutQuoteService checkout;
    public CheckoutPageController(CheckoutQuoteService checkout) { this.checkout = checkout; }

    @InitBinder("quoteForm")
    void fields(WebDataBinder binder) { binder.setAllowedFields("addressId", "shippingProviderId", "shippingServiceCode", "voucherCode"); }

    @GetMapping
    String view(@RequestParam(required = false) Long addressId, Model model) {
        var addresses = checkout.addresses();
        model.addAttribute("addresses", addresses);
        Long chosen = addressId != null ? addressId : addresses.isEmpty() ? null : addresses.getFirst().id();
        if (chosen != null && addresses.stream().noneMatch(address -> address.id().equals(chosen))) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        model.addAttribute("addressId", chosen);
        try {
            model.addAttribute("shippingOptions", chosen == null ? java.util.List.of() : checkout.shippingOptions(chosen));
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.VALIDATION_FAILED
                    && exception.errorCode() != ErrorCode.RESOURCE_NOT_FOUND) throw exception;
            // USER-02 allows address codes outside SHIP-00's supported format. Render an actionable
            // empty state instead of repeatedly redirecting to the same unsupported default address.
            model.addAttribute("shippingOptions", java.util.List.of());
            model.addAttribute("errorMessage", "Địa chỉ này chưa được hỗ trợ vận chuyển. Vui lòng chọn hoặc cập nhật địa chỉ.");
        }
        return "checkout/quote";
    }

    @PostMapping
    String quote(@Valid @ModelAttribute("quoteForm") QuoteRequest request, BindingResult binding, Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Vui lòng chọn địa chỉ và dịch vụ vận chuyển hợp lệ.");
            return "redirect:/user/checkout/view";
        }
        model.addAttribute("preview", checkout.quote(request));
        model.addAttribute("placeOrderKey", java.util.UUID.randomUUID().toString());
        return view(request.addressId(), model);
    }

    @ExceptionHandler(ApplicationException.class)
    String error(ApplicationException exception, RedirectAttributes redirect) {
        if (exception.errorCode() == ErrorCode.UNAUTHENTICATED) throw exception;
        if (exception.detail() != null) {
            redirect.addFlashAttribute("errorMessage", exception.publicMessage());
            return "redirect:/user/checkout/view";
        }
        redirect.addFlashAttribute("errorMessage", switch (exception.errorCode()) {
            case INVALID_REQUEST -> "Giỏ hàng trống hoặc chưa chọn sản phẩm. Vui lòng kiểm tra giỏ hàng.";
            case CONFLICT -> "Có sản phẩm đã chọn không còn bán hoặc vượt tồn kho. Vui lòng kiểm tra giỏ hàng.";
            case RESOURCE_NOT_FOUND -> "Địa chỉ, sản phẩm hoặc dịch vụ vận chuyển không khả dụng.";
            default -> "Không thể tính báo giá với lựa chọn hiện tại. Vui lòng kiểm tra lại.";
        });
        return "redirect:/user/checkout/view";
    }
}
