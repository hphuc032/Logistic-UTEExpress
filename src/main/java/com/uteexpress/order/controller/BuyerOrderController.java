package com.uteexpress.order.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.BuyerOrderDetail;
import com.uteexpress.order.dto.BuyerOrderPage;
import com.uteexpress.order.service.BuyerOrderService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/orders")
public class BuyerOrderController {
    private final BuyerOrderService orders;

    public BuyerOrderController(BuyerOrderService orders) { this.orders = orders; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    BuyerOrderPage list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orders.list(page, size);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    String listPage(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        model.addAttribute("orders", orders.list(page, size));
        return "order/list";
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    BuyerOrderDetail detail(@PathVariable Long id) { return orders.detail(id); }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    String detailPage(@PathVariable Long id, Model model) {
        model.addAttribute("order", orders.detail(id));
        return "order/detail";
    }

    // Strict HTML requests need a renderable error representation; JSON keeps the shared advice.
    @ExceptionHandler(value = ApplicationException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView pageError(ApplicationException exception) {
        return errorPage(exception.errorCode());
    }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView invalidPageInput(MethodArgumentTypeMismatchException exception) {
        return errorPage(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(value = AccessDeniedException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView deniedPage(AccessDeniedException exception) {
        return errorPage(ErrorCode.ACCESS_DENIED);
    }

    private static ModelAndView errorPage(ErrorCode code) {
        return new ModelAndView("order/error", Map.of("errorCode", code.name(),
                "publicMessage", code.message()), code.status());
    }
}
