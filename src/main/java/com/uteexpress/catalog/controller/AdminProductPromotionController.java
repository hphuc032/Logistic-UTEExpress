package com.uteexpress.catalog.controller;

import com.uteexpress.catalog.service.AdminProductPromotionService;
import com.uteexpress.common.exception.*;
import com.uteexpress.promotion.dto.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.support.ExtendedServletRequestDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller

public class AdminProductPromotionController {
    private static final String FORM = "admin/promotions/form";
    private final AdminProductPromotionService management;
    public AdminProductPromotionController(AdminProductPromotionService management) { this.management = management; }
    @GetMapping("/admin/promotions")
    String products(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("catalog",management.products(q,page)); return "admin/promotions/products";
    }
    @InitBinder("promotionForm")
    void fields(WebDataBinder binder) {
        // Form fields come from request parameters; transport headers are not business input.
        if (binder instanceof ExtendedServletRequestDataBinder servletBinder) {
            servletBinder.setHeaderPredicate(header -> false);
        }
        binder.setAllowedFields("name", "discountPercent", "startsAt", "endsAt", "active", "version");
    }
    @InitBinder("disableForm")
    void disableFields(WebDataBinder binder) {
        if (binder instanceof ExtendedServletRequestDataBinder servletBinder) {
            servletBinder.setHeaderPredicate(header -> false);
        }
        binder.setAllowedFields("version");
    }
    @GetMapping("/admin/promotions/products/{productId}")
    String list(@PathVariable Long productId, Model model) {
        model.addAttribute("product", management.product(productId));
        model.addAttribute("promotions", management.list(productId));
        return "admin/promotions/list";
    }
    @GetMapping("/admin/promotions/products/{productId}/new")
    String createForm(@PathVariable Long productId, Model model) {
        prepare(productId, null, model);
        model.addAttribute("promotionForm", new AdminPromotionForm()); return FORM;
    }
    @GetMapping("/admin/promotions/products/{productId}/{id}/edit")
    String editForm(@PathVariable Long productId, @PathVariable Long id, Model model) {
        var promotion = management.get(productId, id);
        var form = new AdminPromotionUpdateForm();
        form.setName(promotion.name()); form.setDiscountPercent(promotion.discountPercent());
        form.setStartsAt(promotion.startsAt()); form.setEndsAt(promotion.endsAt());
        form.setActive(promotion.active()); form.setVersion(promotion.version());
        prepare(productId, promotion, model); model.addAttribute("promotionForm", form); return FORM;
    }
    @PostMapping("/admin/promotions/products/{productId}")
    String create(@PathVariable Long productId, @Valid @ModelAttribute("promotionForm") AdminPromotionForm form,
            BindingResult binding, Model model, RedirectAttributes redirect, HttpServletRequest request) {
        prepare(productId, null, model); rejectTampering(binding, request);
        if (binding.hasErrors()) return FORM;
        final Long id;
        try { id = management.create(productId, form); }
        catch (ApplicationException error) { return formError(error, binding); }
        redirect.addFlashAttribute("successMessage", "Đã tạo khuyến mãi sản phẩm.");
        return "redirect:" + base(productId) + "/" + id + "/edit";
    }
    @PostMapping("/admin/promotions/products/{productId}/{id}/update")
    String update(@PathVariable Long productId, @PathVariable Long id,
            @Valid @ModelAttribute("promotionForm") AdminPromotionUpdateForm form,
            BindingResult binding, Model model, RedirectAttributes redirect, HttpServletRequest request) {
        prepare(productId, management.get(productId, id), model); rejectTampering(binding, request);
        if (binding.hasErrors()) return FORM;
        try { management.update(productId, id, form); }
        catch (ApplicationException error) { return formError(error, binding); }
        redirect.addFlashAttribute("successMessage", "Đã cập nhật khuyến mãi.");
        return "redirect:" + base(productId) + "/" + id + "/edit";
    }
    @GetMapping("/admin/promotions/products/{productId}/{id}/disable")
    String confirmDisable(@PathVariable Long productId, @PathVariable Long id, Model model) {
        prepare(productId,management.get(productId,id),model);
        return "admin/promotions/confirm-disable";
    }

    @PostMapping("/admin/promotions/products/{productId}/{id}/disable")
    String disable(@PathVariable Long productId, @PathVariable Long id,
            @Valid @ModelAttribute("disableForm") PromotionDisableForm form, BindingResult binding,
            RedirectAttributes redirect, HttpServletRequest request) {
        management.get(productId, id); rejectTampering(binding, request);
        if (binding.hasErrors()) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        management.disable(productId, id, form.getVersion());
        redirect.addFlashAttribute("successMessage", "Đã tắt khuyến mãi. Giá đơn hàng cũ được giữ nguyên.");
        return "redirect:" + base(productId);
    }
    private void prepare(Long productId, PromotionView promotion, Model model) {
        model.addAttribute("product", management.product(productId)); model.addAttribute("promotion", promotion);
        model.addAttribute("editMode", promotion != null);
    }
    private static void rejectTampering(BindingResult binding, HttpServletRequest request) {
        // MVC also offers URI variables for binding. Suppress them, but reject identifiers
        // supplied by the client even when their values match the authorized URL.
        boolean identifiersSubmitted = request.getParameterMap().containsKey("productId")
                || request.getParameterMap().containsKey("id");
        boolean otherFieldsSuppressed = Arrays.stream(binding.getSuppressedFields())
                .anyMatch(field -> !field.equals("productId") && !field.equals("id"));
        if (identifiersSubmitted || otherFieldsSuppressed) {
            binding.reject("invalidFields", "Biểu mẫu chứa trường không được phép.");
        }
    }
    private static String formError(ApplicationException error, BindingResult binding) {
        if (error.errorCode() == ErrorCode.CONFLICT) {
            binding.reject("conflict", "Lịch khuyến mãi bị trùng hoặc dữ liệu đã thay đổi. Vui lòng kiểm tra và tải lại trang.");
        } else if (error.errorCode() == ErrorCode.VALIDATION_FAILED) {
            binding.reject("invalidPromotion", "Thông tin khuyến mãi không hợp lệ.");
        } else throw error;
        return FORM;
    }
    private static String base(Long productId) { return "/admin/promotions/products/" + productId; }
    @ExceptionHandler(ApplicationException.class)
    ModelAndView error(ApplicationException error) { return error(error.errorCode()); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ModelAndView invalidId(MethodArgumentTypeMismatchException error) { return error(ErrorCode.INVALID_REQUEST); }
    private static ModelAndView error(ErrorCode code) {
        return new ModelAndView("admin/promotions/error", Map.of("publicMessage", code.message()), code.status());
    }
}
