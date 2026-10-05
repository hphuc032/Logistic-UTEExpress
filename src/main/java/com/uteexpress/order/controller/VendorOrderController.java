package com.uteexpress.order.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.order.service.VendorOrderService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/vendor/orders")
public class VendorOrderController {
    private final VendorOrderService reads;
    private final OrderLifecycleService lifecycle;

    public VendorOrderController(VendorOrderService reads, OrderLifecycleService lifecycle) {
        this.reads = reads;
        this.lifecycle = lifecycle;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    VendorOrderPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reads.list(page, size);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    String listPage(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Model model) {
        model.addAttribute("orders", reads.list(page, size));
        return "vendor/orders/list";
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    VendorOrderDetail detail(@PathVariable Long id) { return reads.detail(id); }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    String detailPage(@PathVariable Long id, Model model) {
        var order = reads.detail(id);
        model.addAttribute("order", order.facts());
        model.addAttribute("vendorOrder", order);
        return "vendor/orders/detail";
    }

    @PostMapping(value = "/{id}/{operation:confirm|ready|cancel}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    Map<String, String> mutate(@PathVariable Long id, @PathVariable String operation,
            @Valid @RequestBody VendorOrderMutation request) {
        perform(id, operation, request);
        return Map.of("result", "OK");
    }

    @PostMapping(value = "/{id}/{operation:confirm|ready|cancel}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String mutateForm(@PathVariable Long id, @PathVariable String operation, @Valid VendorOrderMutation request) {
        perform(id, operation, request);
        return "redirect:/vendor/orders/" + id;
    }

    private void perform(Long id, String operation, VendorOrderMutation request) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        if (operation.equals("ready")) {
            lifecycle.markReady(new OrderReadyCommand(id, request.expectedVersion()));
        } else {
            // Ownership-safe read selects cancellation intent; mutation reloads and checks state/version under lock.
            var status = operation.equals("confirm") ? OrderStatus.NEW : reads.detail(id).facts().status();
            var action = operation.equals("confirm") ? OrderAction.CONFIRM
                    : status == OrderStatus.NEW ? OrderAction.CANCEL_NEW : OrderAction.CANCEL_CONFIRMED;
            lifecycle.transition(new OrderTransitionCommand(id, status, request.expectedVersion(), action, request.reason()));
        }
    }

    @ExceptionHandler(value = ApplicationException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView pageError(ApplicationException error) { return errorPage(error.errorCode()); }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView invalidId(MethodArgumentTypeMismatchException error) { return errorPage(ErrorCode.INVALID_REQUEST); }

    @ExceptionHandler(value = org.springframework.web.bind.MethodArgumentNotValidException.class,
            produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView invalidForm(org.springframework.web.bind.MethodArgumentNotValidException error) {
        return errorPage(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(value = AccessDeniedException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView denied(AccessDeniedException error) { return errorPage(ErrorCode.ACCESS_DENIED); }

    private static ModelAndView errorPage(ErrorCode code) {
        return new ModelAndView("order/error", Map.of("errorCode", code.name(), "publicMessage", code.message()), code.status());
    }
}
