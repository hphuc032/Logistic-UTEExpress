package com.uteexpress.catalog.controller;

import com.uteexpress.catalog.dto.PublicHomeView;
import com.uteexpress.catalog.service.PublicCatalogService;
import com.uteexpress.common.storage.StoredContent;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PublicCatalogController {
    private final PublicCatalogService catalog;

    public PublicCatalogController(PublicCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/")
    String home(Model model) {
        PublicHomeView home = catalog.home();
        model.addAttribute("products", home.products());
        model.addAttribute("categories", home.categories());
        model.addAttribute("shops", home.shops());
        return "index";
    }

    @GetMapping("/products")
    String products(Model model) {
        model.addAttribute("products", catalog.products());
        return "products/list";
    }

    @GetMapping("/products/{id}")
    String product(@PathVariable Long id, Model model) {
        model.addAttribute("product", catalog.product(id));
        return "products/detail";
    }

    @GetMapping("/categories/{slug}")
    String category(@PathVariable String slug, Model model) {
        model.addAttribute("category", catalog.category(slug));
        return "categories/detail";
    }

    @GetMapping("/shops")
    String shops(Model model) {
        model.addAttribute("shops", catalog.shops());
        return "shops/list";
    }

    @GetMapping("/shops/{slug}")
    String shop(@PathVariable String slug, Model model) {
        model.addAttribute("shop", catalog.shop(slug));
        return "shops/detail";
    }

    @GetMapping("/products/{productId}/images/{imageId}/content")
    ResponseEntity<byte[]> image(@PathVariable Long productId, @PathVariable Long imageId) {
        StoredContent content = catalog.image(productId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mediaType()))
                .contentLength(content.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }
}
