package com.uteexpress.shop.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.shop.dto.ShopRegistrationForm;
import com.uteexpress.shop.dto.ShopRegistrationRequest;
import com.uteexpress.shop.service.ShopRegistrationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ShopRegistrationController {
    private final ShopRegistrationService shops;

    public ShopRegistrationController(ShopRegistrationService shops) {
        this.shops = shops;
    }

    @InitBinder("shopRegistrationForm")
    void restrictRegistrationFields(WebDataBinder binder) {
        binder.setAllowedFields("name", "slug", "description", "pickupAddress");
    }

    @GetMapping("/user/shop")
    String status(Model model) {
        model.addAttribute("shop", shops.currentShop().orElse(null));
        return "shop/application";
    }

    @GetMapping("/user/shop/register")
    String registrationForm(Model model) {
        if (shops.currentShop().isPresent()) {
            return "redirect:/user/shop";
        }
        if (!model.containsAttribute("shopRegistrationForm")) {
            model.addAttribute("shopRegistrationForm", new ShopRegistrationForm());
        }
        return "shop/register";
    }

    @PostMapping("/user/shop/register")
    String register(@Valid @ModelAttribute ShopRegistrationForm shopRegistrationForm,
            BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "shop/register";
        }
        try {
            shops.register(new ShopRegistrationRequest(
                    shopRegistrationForm.getName(), shopRegistrationForm.getSlug(),
                    shopRegistrationForm.getDescription(), shopRegistrationForm.getPickupAddress()));
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            binding.reject("shop.registration.conflict",
                    "Không thể gửi đơn với thông tin đã cung cấp. Bạn có thể đã có cửa hàng hoặc slug đã được sử dụng.");
            return "shop/register";
        }
        redirect.addFlashAttribute("successMessage", "Đơn đăng ký cửa hàng đã được gửi và đang chờ xét duyệt.");
        return "redirect:/user/shop";
    }
}
