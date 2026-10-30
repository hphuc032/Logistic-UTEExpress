package com.uteexpress.payment.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.PaymentRecordView;
import com.uteexpress.payment.service.PaymentService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/** Persisted buyer status only. There is deliberately no HTTP collection/status mutation operation. */
@Controller
@RequestMapping("/orders/{orderId}/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) { this.payments = payments; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    List<PaymentRecordView> records(@PathVariable Long orderId) { return payments.recordsForBuyer(orderId); }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    String statusPage(@PathVariable Long orderId) {
        payments.recordsForBuyer(orderId);
        return "redirect:/orders/" + orderId + "#payment-title";
    }

    @ExceptionHandler(value = ApplicationException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView pageError(ApplicationException exception) { return errorPage(exception.errorCode()); }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView invalidInput(MethodArgumentTypeMismatchException exception) { return errorPage(ErrorCode.INVALID_REQUEST); }

    @ExceptionHandler(value = AccessDeniedException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView denied(AccessDeniedException exception) { return errorPage(ErrorCode.ACCESS_DENIED); }

    private static ModelAndView errorPage(ErrorCode code) {
        return new ModelAndView("order/error", Map.of("errorCode", code.name(), "publicMessage", code.message()), code.status());
    }
}
