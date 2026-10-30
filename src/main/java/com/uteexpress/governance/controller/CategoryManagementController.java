package com.uteexpress.governance.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.CategoryRequest;
import com.uteexpress.governance.dto.CategoryView;
import com.uteexpress.governance.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/{ops:admin|manager}/categories")
public class CategoryManagementController {
    private final CategoryService categories;
    public CategoryManagementController(CategoryService categories) { this.categories = categories; }

    @ModelAttribute("basePath")
    String basePath(@PathVariable String ops) { return "/" + ops + "/categories"; }

    @GetMapping
    String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("categories", categories.list(page));
        return "governance/categories/list";
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute("categoryRequest", new CategoryRequest("", "", null));
        return "governance/categories/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        CategoryView category = categories.get(id);
        model.addAttribute("categoryId", category.id());
        model.addAttribute("categoryRequest", new CategoryRequest(category.name(), category.slug(), category.version()));
        return "governance/categories/form";
    }

    @PostMapping
    String create(@PathVariable String ops, @Valid @ModelAttribute CategoryRequest categoryRequest,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) return "governance/categories/form";
        try { categories.create(categoryRequest); }
        catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            binding.rejectValue("slug", "duplicate", "Slug đã tồn tại. Vui lòng chọn slug khác.");
            return "governance/categories/form";
        }
        redirect.addFlashAttribute("successMessage", "Đã tạo danh mục.");
        return "redirect:/" + ops + "/categories";
    }

    @PostMapping("/{id}/update")
    String update(@PathVariable String ops, @PathVariable Long id,
            @Valid @ModelAttribute CategoryRequest categoryRequest, BindingResult binding,
            Model model, RedirectAttributes redirect) {
        model.addAttribute("categoryId", id);
        if (binding.hasErrors()) return "governance/categories/form";
        try { categories.update(id, categoryRequest); }
        catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            model.addAttribute("errorMessage", "Slug đã tồn tại hoặc danh mục vừa được người khác sửa. Hãy tải lại trước khi lưu.");
            return "governance/categories/form";
        }
        redirect.addFlashAttribute("successMessage", "Đã cập nhật danh mục.");
        return "redirect:/" + ops + "/categories";
    }

    @PostMapping("/{id}/{action:enable|disable}")
    String visibility(@PathVariable String ops, @PathVariable Long id, @PathVariable String action,
            @RequestParam Long version, RedirectAttributes redirect) {
        try {
            categories.setActive(id, version, action.equals("enable"));
            redirect.addFlashAttribute("successMessage", action.equals("enable") ? "Đã bật danh mục." : "Đã ẩn danh mục.");
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            redirect.addFlashAttribute("errorMessage", "Danh mục vừa thay đổi. Hãy kiểm tra trạng thái mới rồi thử lại.");
        }
        return "redirect:/" + ops + "/categories";
    }
}
