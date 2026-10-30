package com.uteexpress.shipping.controller;

import com.uteexpress.common.exception.*;
import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.service.ShippingConfigService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/{ops:admin|manager}/shipping")
public class ShippingConfigController {
    private final ShippingConfigService config;
    public ShippingConfigController(ShippingConfigService config) { this.config=config; }
    @ModelAttribute("basePath") String base(@PathVariable String ops) { return "/"+ops+"/shipping"; }

    @GetMapping("/providers")
    String providers(@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("items",config.providers(page)); return "shipping/providers";
    }
    @GetMapping("/providers/new")
    String newProvider(Model model) {
        model.addAttribute("shippingProviderRequest",new ShippingProviderRequest("","",true,null));
        return "shipping/provider-form";
    }
    @GetMapping("/providers/{id}/edit")
    String provider(@PathVariable Long id,Model model) {
        var p=config.provider(id);
        model.addAttribute("itemId",id);
        model.addAttribute("shippingProviderRequest",new ShippingProviderRequest(p.code(),p.name(),p.active(),p.version()));
        return "shipping/provider-form";
    }
    @PostMapping("/providers")
    String createProvider(@PathVariable String ops,@Valid @ModelAttribute ShippingProviderRequest request,
            BindingResult errors,Model model,RedirectAttributes redirect) {
        if(errors.hasErrors()) return "shipping/provider-form";
        try { config.createProvider(request); } catch(ApplicationException e) {
            if(!expected(e,model)) throw e; return "shipping/provider-form";
        }
        return done(ops,"providers",redirect);
    }
    @PostMapping("/providers/{id}/update")
    String updateProvider(@PathVariable String ops,@PathVariable Long id,
            @Valid @ModelAttribute ShippingProviderRequest request,BindingResult errors,Model model,RedirectAttributes redirect) {
        model.addAttribute("itemId",id);
        if(errors.hasErrors()) return "shipping/provider-form";
        try { config.updateProvider(id,request); } catch(ApplicationException e) {
            if(!expected(e,model)) throw e; return "shipping/provider-form";
        }
        return done(ops,"providers",redirect);
    }
    @PostMapping("/providers/{id}/disable")
    String disableProvider(@PathVariable String ops,@PathVariable Long id,@RequestParam Long version,RedirectAttributes redirect) {
        try { config.disableProvider(id,version); } catch(ApplicationException e) { return conflict(e,ops,"providers",redirect); }
        return done(ops,"providers",redirect);
    }
    @GetMapping("/rates")
    String rates(@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("items",config.rates(page)); return "shipping/rates";
    }
    @GetMapping("/rates/new")
    String newRate(Model model) {
        model.addAttribute("shippingRateRequest",new ShippingRateRequest(null,"","",null,true,null));
        rateOptions(model); return "shipping/rate-form";
    }
    @GetMapping("/rates/{id}/edit")
    String rate(@PathVariable Long id,Model model) {
        var r=config.rate(id);
        model.addAttribute("itemId",id);
        model.addAttribute("providerName",r.providerName());
        model.addAttribute("shippingRateRequest",new ShippingRateRequest(r.providerId(),r.serviceCode(),
                r.destinationRegion(),r.fee().setScale(0),r.active(),r.version()));
        return "shipping/rate-form";
    }
    @PostMapping("/rates")
    String createRate(@PathVariable String ops,@Valid @ModelAttribute ShippingRateRequest request,
            BindingResult errors,Model model,RedirectAttributes redirect) {
        if(errors.hasErrors()) { rateOptions(model); return "shipping/rate-form"; }
        try { config.createRate(request); } catch(ApplicationException e) {
            if(!expected(e,model)) throw e; rateOptions(model); return "shipping/rate-form";
        }
        return done(ops,"rates",redirect);
    }
    @PostMapping("/rates/{id}/update")
    String updateRate(@PathVariable String ops,@PathVariable Long id,
            @Valid @ModelAttribute ShippingRateRequest request,BindingResult errors,Model model,RedirectAttributes redirect) {
        model.addAttribute("itemId",id);
        model.addAttribute("providerName",config.rate(id).providerName());
        if(errors.hasErrors()) return "shipping/rate-form";
        try { config.updateRate(id,request); } catch(ApplicationException e) {
            if(!expected(e,model)) throw e; return "shipping/rate-form";
        }
        return done(ops,"rates",redirect);
    }
    @PostMapping("/rates/{id}/disable")
    String disableRate(@PathVariable String ops,@PathVariable Long id,@RequestParam Long version,RedirectAttributes redirect) {
        try { config.disableRate(id,version); } catch(ApplicationException e) { return conflict(e,ops,"rates",redirect); }
        return done(ops,"rates",redirect);
    }
    private void rateOptions(Model model) { model.addAttribute("providers",config.activeProviders()); }
    private static String done(String ops,String kind,RedirectAttributes redirect) {
        redirect.addFlashAttribute("successMessage","Đã lưu cấu hình vận chuyển.");
        return "redirect:/"+ops+"/shipping/"+kind;
    }
    private static boolean expected(ApplicationException e,Model model) {
        if(e.errorCode()==ErrorCode.CONFLICT) {
            model.addAttribute("errorMessage","Mã cấu hình bị trùng hoặc dữ liệu vừa thay đổi. Hãy tải lại trước khi lưu."); return true;
        }
        if(e.errorCode()==ErrorCode.VALIDATION_FAILED) {
            model.addAttribute("errorMessage","Cấu hình không hợp lệ hoặc đơn vị vận chuyển đã ngừng hoạt động. Vui lòng kiểm tra lại."); return true;
        }
        return false;
    }
    private static String conflict(ApplicationException e,String ops,String kind,RedirectAttributes redirect) {
        if(e.errorCode()!=ErrorCode.CONFLICT) throw e;
        redirect.addFlashAttribute("errorMessage","Dữ liệu vừa thay đổi. Hãy kiểm tra lại phiên bản mới.");
        return "redirect:/"+ops+"/shipping/"+kind;
    }
}
