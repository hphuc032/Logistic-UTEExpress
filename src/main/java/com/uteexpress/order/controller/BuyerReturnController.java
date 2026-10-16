package com.uteexpress.order.controller;

import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/orders/{id}/return")
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class BuyerReturnController extends ReturnControllerSupport {
    private final ReturnQueryService reads;
    private final OrderLifecycleService lifecycle;
    public BuyerReturnController(ReturnQueryService reads, OrderLifecycleService lifecycle) {
        this.reads = reads; this.lifecycle = lifecycle;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    ReturnManagementView detail(@PathVariable Long id, HttpServletRequest request) {
        parameters(request);
        return reads.buyer(id);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    String page(@PathVariable Long id, HttpServletRequest request, Model model) {
        parameters(request);
        model.addAttribute("returnView", reads.buyer(id));
        model.addAttribute("vendor", false);
        return "order/return";
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    Map<String, String> request(@PathVariable Long id, @Valid @RequestBody ReturnRequestInput input,
            HttpServletRequest request) {
        parameters(request);
        perform(id, input);
        return Map.of("result", "OK");
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String requestForm(@PathVariable Long id, @Valid ReturnRequestInput input, HttpServletRequest request) {
        parameters(request, "expectedVersion", "reason");
        perform(id, input);
        return "redirect:/orders/" + id + "/return";
    }

    private void perform(Long id, ReturnRequestInput input) {
        lifecycle.requestReturn(new RequestReturnCommand(id, input.expectedVersion(), input.reason()));
    }
}
