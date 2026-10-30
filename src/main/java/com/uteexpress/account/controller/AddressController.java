package com.uteexpress.account.controller;

import com.uteexpress.account.dto.AddressForm;
import com.uteexpress.account.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AddressController {
    private static final String VIEW = "account/addresses";
    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @InitBinder({"createForm", "editForm"})
    void restrictAddressFields(WebDataBinder binder) {
        binder.setAllowedFields("receiverName", "phone", "provinceCode", "district", "detail");
    }

    @GetMapping("/user/addresses")
    String list(Model model) {
        populate(model);
        return VIEW;
    }

    @PostMapping("/user/addresses")
    String create(@Valid @ModelAttribute("createForm") AddressForm form, BindingResult binding,
            Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            populate(model);
            return VIEW;
        }
        addresses.create(form);
        redirect.addFlashAttribute("successMessage", "Địa chỉ đã được thêm.");
        return "redirect:/user/addresses";
    }

    @PostMapping("/user/addresses/{id}/update")
    String update(@PathVariable Long id, @Valid @ModelAttribute("editForm") AddressForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editingAddressId", id);
            populate(model);
            return VIEW;
        }
        addresses.update(id, form);
        redirect.addFlashAttribute("successMessage", "Địa chỉ đã được cập nhật.");
        return "redirect:/user/addresses";
    }

    @PostMapping("/user/addresses/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        addresses.delete(id);
        redirect.addFlashAttribute("successMessage", "Địa chỉ đã được xóa.");
        return "redirect:/user/addresses";
    }

    @PostMapping("/user/addresses/{id}/default")
    String setDefault(@PathVariable Long id, RedirectAttributes redirect) {
        addresses.setDefault(id);
        redirect.addFlashAttribute("successMessage", "Địa chỉ mặc định đã được cập nhật.");
        return "redirect:/user/addresses";
    }

    private void populate(Model model) {
        model.addAttribute("addresses", addresses.listCurrentAddresses());
        if (!model.containsAttribute("createForm")) {
            model.addAttribute("createForm", new AddressForm());
        }
    }
}
