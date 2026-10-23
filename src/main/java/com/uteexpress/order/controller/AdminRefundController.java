package com.uteexpress.order.controller;

import com.uteexpress.common.exception.*;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.payment.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders/{orderId}/refunds")
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
public class AdminRefundController {
    private final OrderLifecycleService lifecycle;
    public AdminRefundController(OrderLifecycleService lifecycle) { this.lifecycle = lifecycle; }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public RefundResult refund(@PathVariable Long orderId, @Valid @RequestBody ManualRefundRequest body,
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        if (Collections.list(request.getHeaders("Idempotency-Key")).size() != 1
                || request.getParameterMap().keySet().stream().anyMatch(p -> !p.equals("_csrf"))
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return lifecycle.completeRefund(new ManualRefundCommand(orderId, body.expectedOrderVersion(),
                body.expectedReturnRequestVersion(), body.proofReference(), key));
    }
}
