package com.uteexpress.governance.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.service.ProductModerationService;
import com.uteexpress.governance.service.ShopModerationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/{ops:admin|manager}/moderation")
public class ModerationController {
    private final ProductModerationService products;
    private final ShopModerationService shops;

    public ModerationController(ProductModerationService products, ShopModerationService shops) {
        this.products = products;
        this.shops = shops;
    }

    @GetMapping("/{kind:products|shops}")
    String list(@PathVariable String ops, @PathVariable String kind,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("ops", ops);
        model.addAttribute("kind", kind);
        model.addAttribute("query", query);
        model.addAttribute("items", "products".equals(kind) ? products.search(query, page) : shops.search(query, page));
        return "governance/moderation/list";
    }

    @PostMapping("/products/{id}/{action:hide|restore}")
    String product(@PathVariable String ops, @PathVariable Long id, @PathVariable String action,
            @RequestParam Long version, @RequestParam String reason, RedirectAttributes redirect) {
        try {
            products.change(id, version, "hide".equals(action), reason);
            redirect.addFlashAttribute("successMessage", "Đã cập nhật sản phẩm.");
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            redirect.addFlashAttribute("errorMessage", "Sản phẩm đã thay đổi hoặc không thể thực hiện thao tác. Hãy tải lại danh sách.");
        }
        return "redirect:/" + ops + "/moderation/products";
    }

    @PostMapping("/shops/{id}/{action:suspend|resume}")
    String shop(@PathVariable String ops, @PathVariable Long id, @PathVariable String action,
            @RequestParam Long version, @RequestParam String reason, RedirectAttributes redirect) {
        try {
            shops.change(id, version, "suspend".equals(action), reason);
            redirect.addFlashAttribute("successMessage", "Đã cập nhật cửa hàng.");
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            redirect.addFlashAttribute("errorMessage", "Cửa hàng đã thay đổi hoặc không thể thực hiện thao tác. Hãy tải lại danh sách.");
        }
        return "redirect:/" + ops + "/moderation/shops";
    }
}
