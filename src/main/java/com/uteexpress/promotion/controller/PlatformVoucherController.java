package com.uteexpress.promotion.controller;

import com.uteexpress.common.exception.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.PlatformVoucherManagementService;
import jakarta.validation.Valid;
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
@RequestMapping("/admin/vouchers")
public class PlatformVoucherController {
    private static final String FORM_VIEW = "admin/vouchers/form";
    private final PlatformVoucherManagementService vouchers;

    public PlatformVoucherController(PlatformVoucherManagementService vouchers) { this.vouchers = vouchers; }

    @InitBinder("voucherForm")
    void restrictFields(WebDataBinder binder, org.springframework.web.context.request.WebRequest request) {
        if (request.getParameter("id") != null) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        // Form fields come from request parameters; transport headers are not business input.
        if (binder instanceof ExtendedServletRequestDataBinder servletBinder) {
            servletBinder.setHeaderPredicate(header -> false);
        }
        binder.setAllowedFields("code", "type", "value", "maxDiscount", "minSubtotal", "startsAt",
                "endsAt", "totalLimit", "perUserLimit", "active", "version", "id");
        // MVC also offers URI variables to the binder. Neither form has an id property;
        // resource identity is exclusively the @PathVariable and the owned service query.
    }

    @InitBinder("versionForm")
    void restrictVersion(WebDataBinder binder, org.springframework.web.context.request.WebRequest request) {
        if (request.getParameter("id") != null) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        if (binder instanceof ExtendedServletRequestDataBinder servletBinder) servletBinder.setHeaderPredicate(header -> false);
        binder.setAllowedFields("version", "id");
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("vouchers", vouchers.list());
        return "admin/vouchers/list";
    }

    @GetMapping("/new")
    String createForm(Model model) {
        vouchers.requireAdministrator();
        model.addAttribute("voucherForm", new PlatformVoucherForm());
        prepareForm(model, null);
        return FORM_VIEW;
    }

    @GetMapping({"/{id}", "/{id}/edit"})
    String editForm(@PathVariable Long id, Model model) {
        var voucher = vouchers.get(id);
        model.addAttribute("voucherForm", updateForm(voucher));
        prepareForm(model, voucher);
        return FORM_VIEW;
    }

    @PostMapping
    String create(@Valid @ModelAttribute("voucherForm") PlatformVoucherForm form, BindingResult binding,
            Model model, RedirectAttributes redirect) {
        rejectTampering(binding);
        prepareForm(model, null);
        if (binding.hasErrors()) return FORM_VIEW;
        final Long id;
        try { id = vouchers.create(form); }
        catch (ApplicationException error) { return formError(error, binding); }
        redirect.addFlashAttribute("successMessage", "Đã tạo voucher nền tảng.");
        return "redirect:/admin/vouchers/" + id + "/edit";
    }

    @PostMapping("/{id}/update")
    String update(@PathVariable Long id, @Valid @ModelAttribute("voucherForm") PlatformVoucherUpdateForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        // Resolve ownership even when input is invalid, before redisplaying any resource.
        prepareForm(model, vouchers.get(id));
        rejectTampering(binding);
        if (binding.hasErrors()) return FORM_VIEW;
        try { vouchers.update(id, form); }
        catch (ApplicationException error) { return formError(error, binding); }
        redirect.addFlashAttribute("successMessage", "Đã cập nhật voucher.");
        return "redirect:/admin/vouchers/" + id + "/edit";
    }

    @GetMapping("/{id}/disable")
    String confirmDisable(@PathVariable Long id, Model model) {
        model.addAttribute("voucher", vouchers.get(id));
        return "admin/vouchers/confirm-disable";
    }

    @PostMapping("/{id}/disable")
    String disable(@PathVariable Long id, @Valid @ModelAttribute("versionForm") VoucherVersionForm form,
            BindingResult binding, RedirectAttributes redirect) {
        vouchers.get(id);
        rejectTampering(binding);
        if (binding.hasErrors()) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        vouchers.disable(id, form);
        redirect.addFlashAttribute("successMessage", "Đã ngừng voucher. Lịch sử sử dụng được giữ nguyên.");
        return "redirect:/admin/vouchers";
    }

    private void prepareForm(Model model, PlatformVoucherView voucher) {
        model.addAttribute("editMode", voucher != null);
        model.addAttribute("voucher", voucher);
        model.addAttribute("types", VoucherType.values());
    }

    private static void rejectTampering(BindingResult binding) {
        if (binding.getSuppressedFields().length > 0) {
            binding.reject("invalidFields", "Biểu mẫu chứa trường không được phép. Vui lòng tải lại trang.");
        }
    }

    private static String formError(ApplicationException error, BindingResult binding) {
        if (error.errorCode() == ErrorCode.CONFLICT) {
            binding.reject("conflict", "Không thể lưu: mã voucher đã tồn tại, dữ liệu đã thay đổi hoặc thay đổi không được phép sau khi sử dụng. Vui lòng kiểm tra và tải lại trang.");
        } else if (error.errorCode() == ErrorCode.VALIDATION_FAILED) {
            binding.reject("invalidVoucher", "Thông tin voucher không hợp lệ. Vui lòng kiểm tra mức giảm, thời hạn và giới hạn sử dụng.");
        } else { throw error; }
        return FORM_VIEW;
    }

    private static PlatformVoucherUpdateForm updateForm(PlatformVoucherView v) {
        var form = new PlatformVoucherUpdateForm();
        form.setCode(v.code());
        form.setType(v.type());
        form.setValue(v.value());
        form.setMaxDiscount(v.maxDiscount());
        form.setMinSubtotal(v.minSubtotal());
        form.setStartsAt(v.startsAt());
        form.setEndsAt(v.endsAt());
        form.setTotalLimit(v.totalLimit());
        form.setPerUserLimit(v.perUserLimit());
        form.setActive(v.active());
        form.setVersion(v.version());
        return form;
    }

    @ExceptionHandler(ApplicationException.class)
    ModelAndView errorPage(ApplicationException error) { return errorPage(error.errorCode()); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ModelAndView invalidId(MethodArgumentTypeMismatchException error) { return errorPage(ErrorCode.INVALID_REQUEST); }
    private static ModelAndView errorPage(ErrorCode code) {
        return new ModelAndView("admin/vouchers/error", Map.of("publicMessage", code.message()), code.status());
    }
}
