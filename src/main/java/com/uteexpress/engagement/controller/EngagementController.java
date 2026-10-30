package com.uteexpress.engagement.controller;

import com.uteexpress.engagement.service.EngagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
public class EngagementController {
    private final EngagementService engagement;

    public EngagementController(EngagementService engagement) { this.engagement = engagement; }

    @GetMapping("/favorites")
    String favorites(Model model) {
        model.addAttribute("items", engagement.favorites());
        model.addAttribute("heading", "Sản phẩm yêu thích");
        model.addAttribute("favorites", true);
        return "engagement/list";
    }

    @PostMapping("/favorites/{productId}")
    String favorite(@PathVariable Long productId, RedirectAttributes redirect) {
        engagement.addFavorite(productId);
        redirect.addFlashAttribute("successMessage", "Đã thêm vào yêu thích.");
        return "redirect:/products/" + productId;
    }

    @PostMapping("/favorites/{productId}/remove")
    String remove(@PathVariable Long productId, RedirectAttributes redirect) {
        engagement.removeFavorite(productId);
        redirect.addFlashAttribute("successMessage", "Đã bỏ khỏi yêu thích.");
        return "redirect:/user/favorites";
    }

    @GetMapping("/recently-viewed")
    String recent(Model model) {
        model.addAttribute("items", engagement.recent());
        model.addAttribute("heading", "Sản phẩm đã xem gần đây");
        model.addAttribute("favorites", false);
        return "engagement/list";
    }

    @PostMapping("/recently-viewed/{productId}")
    String record(@PathVariable Long productId) {
        engagement.recordView(productId);
        return "redirect:/products/" + productId;
    }
}
