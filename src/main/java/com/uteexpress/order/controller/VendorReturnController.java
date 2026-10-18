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
@RequestMapping("/vendor/orders/{id}/return")
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class VendorReturnController extends ReturnControllerSupport {
    private final ReturnQueryService reads;
    private final OrderLifecycleService lifecycle;
    public VendorReturnController(ReturnQueryService reads, OrderLifecycleService lifecycle) {
        this.reads = reads; this.lifecycle = lifecycle;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    ReturnManagementView detail(@PathVariable Long id, HttpServletRequest request) {
        parameters(request);
        return reads.vendor(id);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    String page(@PathVariable Long id, HttpServletRequest request, Model model) {
        parameters(request);
        model.addAttribute("returnView", reads.vendor(id));
        model.addAttribute("vendor", true);
        return "order/return";
    }

    @PostMapping(value = "/{operation:approve|reject}", consumes = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    Map<String, String> decide(@PathVariable Long id, @PathVariable String operation,
            @Valid @RequestBody ReturnDecisionInput input, HttpServletRequest request) {
        parameters(request);
        perform(id, operation, input);
        return Map.of("result", "OK");
    }

    @PostMapping(value = "/{operation:approve|reject}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String decideForm(@PathVariable Long id, @PathVariable String operation,
            @Valid ReturnDecisionInput input, HttpServletRequest request) {
        parameters(request, "expectedVersion", "expectedRequestVersion", "reason");
        perform(id, operation, input);
        return "redirect:/vendor/orders/" + id + "/return";
    }

    @PostMapping(value = "/receive", consumes = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    Map<String, String> receive(@PathVariable Long id, @Valid @RequestBody ReceiveReturnInput input,
            HttpServletRequest request) {
        parameters(request);
        receive(id, input);
        return Map.of("result", "OK");
    }

    @PostMapping(value = "/receive", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String receiveForm(@PathVariable Long id, @Valid ReceiveReturnInput input, HttpServletRequest request) {
        parameters(request, "expectedVersion", "expectedRequestVersion", "receiptReference");
        receive(id, input);
        return "redirect:/vendor/orders/" + id + "/return";
    }

    private void receive(Long id, ReceiveReturnInput input) {
        lifecycle.receiveReturn(new ReceiveReturnCommand(id, input.expectedVersion(),
                input.expectedRequestVersion(), input.receiptReference()));
    }

    private void perform(Long id, String operation, ReturnDecisionInput input) {
        var command = new ReturnDecisionCommand(id, input.expectedVersion(), input.expectedRequestVersion(), input.reason());
        if (operation.equals("approve")) lifecycle.approveReturn(command);
        else lifecycle.rejectReturn(command);
    }
}
