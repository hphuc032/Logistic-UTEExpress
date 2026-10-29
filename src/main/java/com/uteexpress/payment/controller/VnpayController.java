package com.uteexpress.payment.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.payment.dto.VnpayIpnResponse;
import com.uteexpress.payment.service.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ConditionalOnProperty(name = "uteexpress.payment.vnpay.enabled", havingValue = "true")
public class VnpayController {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(VnpayController.class);
    private final VnpayPaymentService payments;
    public VnpayController(VnpayPaymentService payments) { this.payments = payments; }

    @GetMapping("/orders/{orderId}/payments/vnpay")
    String pay(@PathVariable Long orderId, HttpServletRequest request, Model model) {
        try { return "redirect:" + payments.paymentUrl(orderId, request.getRemoteAddr()); }
        catch (ApplicationException ex) {
            if (ex.errorCode() == com.uteexpress.common.exception.ErrorCode.UNAUTHENTICATED
                    || ex.errorCode() == com.uteexpress.common.exception.ErrorCode.ACCESS_DENIED
                    || ex.errorCode() == com.uteexpress.common.exception.ErrorCode.RESOURCE_NOT_FOUND) throw ex;
            model.addAttribute("result", new com.uteexpress.payment.dto.VnpayReturnView(true, orderId, "UNAVAILABLE"));
            return "payment/vnpay-result";
        }
    }

    @GetMapping(value = "/payments/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    VnpayIpnResponse ipn(HttpServletRequest request) {
        var fields = singleValues(request);
        if (fields == null) return VnpayIpnResponse.of("97", "Invalid parameters");
        try { return payments.acceptIpn(fields); }
        catch (RuntimeException ex) {
            // Transaction proxy has already rolled back, including commit/unique-constraint failures.
            // Never return an exception, query string, signature or credential in the IPN response.
            LOG.warn("VNPAY IPN transaction rolled back ({})", ex.getClass().getSimpleName());
            return VnpayIpnResponse.of("99", "Unable to process notification");
        }
    }

    @GetMapping("/payments/vnpay/return")
    String returned(HttpServletRequest request, Model model) {
        var fields = singleValues(request);
        model.addAttribute("result", fields == null
                ? new com.uteexpress.payment.dto.VnpayReturnView(false, null, "INVALID") : payments.returnView(fields));
        return "payment/vnpay-result";
    }

    private static Map<String, String> singleValues(HttpServletRequest request) {
        var fields = new TreeMap<String, String>();
        for (var entry : request.getParameterMap().entrySet()) {
            if (entry.getValue().length != 1 || entry.getValue()[0].length() > 4096) return null;
            if (entry.getKey().startsWith("vnp_")) fields.put(entry.getKey(), entry.getValue()[0]);
        }
        return fields;
    }
}
