package com.uteexpress.catalog.controller;

import com.uteexpress.catalog.dto.ProductCreateRequest;
import com.uteexpress.catalog.dto.ProductUpdateRequest;
import com.uteexpress.catalog.dto.ProductView;
import com.uteexpress.catalog.service.ProductService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.StoredContent;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.governance.service.CategoryQueryService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class VendorProductController {
    private static final String LIST_VIEW = "vendor/products/list";
    private static final String FORM_VIEW = "vendor/products/form";
    private final ProductService products;
    private final CategoryQueryService categories;

    public VendorProductController(ProductService products, CategoryQueryService categories) {
        this.products = products;
        this.categories = categories;
    }

    @InitBinder("productForm")
    void restrictProductFields(WebDataBinder binder) {
        binder.setAllowedFields("name", "description", "price", "stock", "categoryId", "version");
    }

    @GetMapping("/vendor/products")
    String list(Model model) {
        model.addAttribute("products", products.listCurrentShopProducts());
        return LIST_VIEW;
    }

    @GetMapping("/vendor/products/new")
    String createForm(Model model) {
        model.addAttribute("productForm", new ProductCreateRequest());
        prepareForm(model, false, null);
        return FORM_VIEW;
    }

    @GetMapping("/vendor/products/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        ProductView product = products.getCurrentShopProduct(id);
        model.addAttribute("productForm", updateRequest(product));
        prepareForm(model, true, product);
        return FORM_VIEW;
    }

    @PostMapping("/vendor/products")
    String create(@Valid @ModelAttribute("productForm") ProductCreateRequest request,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            prepareForm(model, false, null);
            return FORM_VIEW;
        }
        ProductView created = products.create(request);
        redirect.addFlashAttribute("successMessage", "Sản phẩm đã được tạo.");
        return "redirect:/vendor/products/" + created.id() + "/edit";
    }

    @PostMapping("/vendor/products/{id}/update")
    String update(@PathVariable Long id, @Valid @ModelAttribute("productForm") ProductUpdateRequest request,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            prepareForm(model, true, products.getCurrentShopProduct(id));
            return FORM_VIEW;
        }
        products.update(id, request);
        redirect.addFlashAttribute("successMessage", "Sản phẩm đã được cập nhật.");
        return "redirect:/vendor/products/" + id + "/edit";
    }

    @PostMapping("/vendor/products/{id}/delete")
    String hide(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
        products.hide(id, version);
        redirect.addFlashAttribute("successMessage", "Sản phẩm đã được ẩn.");
        return "redirect:/vendor/products";
    }

    @PostMapping("/vendor/products/{id}/images")
    String uploadImage(@PathVariable Long id, @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String altText, RedirectAttributes redirect) {
        try {
            products.addImage(id, new UploadContent(image.getBytes(), image.getOriginalFilename(),
                    image.getContentType()), altText);
        } catch (IOException invalidUpload) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        redirect.addFlashAttribute("successMessage", "Ảnh sản phẩm đã được thêm.");
        return "redirect:/vendor/products/" + id + "/edit";
    }

    @PostMapping("/vendor/products/{id}/images/{imageId}/delete")
    String deleteImage(@PathVariable Long id, @PathVariable Long imageId, RedirectAttributes redirect) {
        products.deleteImage(id, imageId);
        redirect.addFlashAttribute("successMessage", "Ảnh sản phẩm đã được xóa.");
        return "redirect:/vendor/products/" + id + "/edit";
    }

    @GetMapping("/vendor/products/{id}/images/{imageId}/content")
    ResponseEntity<byte[]> imageContent(@PathVariable Long id, @PathVariable Long imageId) {
        StoredContent content = products.readImage(id, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mediaType()))
                .contentLength(content.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }

    private void prepareForm(Model model, boolean editMode, ProductView product) {
        model.addAttribute("editMode", editMode);
        model.addAttribute("product", product);
        model.addAttribute("categories", categories.listActiveCategories());
        model.addAttribute("maxImages", ProductService.MAX_IMAGES);
        model.addAttribute("maxImageBytes", ProductService.IMAGE_MAX_BYTES);
    }

    private static ProductUpdateRequest updateRequest(ProductView product) {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName(product.name());
        request.setDescription(product.description());
        request.setPrice(product.price());
        request.setStock(product.stock());
        request.setCategoryId(product.categoryId());
        request.setVersion(product.version());
        return request;
    }
}
