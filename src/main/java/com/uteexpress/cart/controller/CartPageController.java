package com.uteexpress.cart.controller;

import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/user/cart/view")
public class CartPageController {
    private static final String REDIRECT = "redirect:/user/cart/view";
    private final CartService carts;

    public CartPageController(CartService carts) { this.carts = carts; }

    @InitBinder("quantityForm")
    void quantityFields(WebDataBinder binder) { binder.setAllowedFields("quantity"); }

    @InitBinder("selectionForm")
    void selectionFields(WebDataBinder binder) { binder.setAllowedFields("selected"); }

    @GetMapping
    String current(Model model) {
        model.addAttribute("cart", carts.getCurrentUserCart()
                .orElseGet(() -> new CartView(null, List.of(), BigDecimal.ZERO)));
        return "cart/view";
    }

    @PostMapping("/items/{id}/quantity")
    String update(@PathVariable Long id,
            @Valid @ModelAttribute("quantityForm") UpdateCartQuantityRequest request,
            BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Số lượng phải là số nguyên lớn hơn 0.");
            return REDIRECT;
        }
        carts.updateQuantity(id, request);
        redirect.addFlashAttribute("successMessage", "Đã cập nhật số lượng.");
        return REDIRECT;
    }

    @PostMapping("/items/{id}/selection")
    String select(@PathVariable Long id, @Valid @ModelAttribute("selectionForm") CartSelectionForm form,
            BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Trạng thái chọn không hợp lệ.");
            return REDIRECT;
        }
        carts.selectItem(id, new SelectCartItemRequest(form.getSelected()));
        redirect.addFlashAttribute("successMessage", "Đã lưu lựa chọn.");
        return REDIRECT;
    }

    @PostMapping("/items/{id}/remove")
    String remove(@PathVariable Long id, RedirectAttributes redirect) {
        carts.removeItem(id);
        redirect.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng.");
        return REDIRECT;
    }

    @ExceptionHandler(ApplicationException.class)
    String businessError(ApplicationException exception, RedirectAttributes redirect) {
        if (exception.errorCode() == ErrorCode.CONFLICT) {
            redirect.addFlashAttribute("errorMessage", "Số lượng vượt tồn kho hiện tại. Vui lòng kiểm tra lại giỏ hàng.");
        } else if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            redirect.addFlashAttribute("errorMessage", "Không tìm thấy mặt hàng hoặc sản phẩm không còn bán.");
        } else if (exception.errorCode() == ErrorCode.VALIDATION_FAILED) {
            redirect.addFlashAttribute("errorMessage", "Thông tin cập nhật không hợp lệ.");
        } else {
            throw exception;
        }
        return REDIRECT;
    }
}
