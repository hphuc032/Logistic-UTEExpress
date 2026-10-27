package com.uteexpress.order.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.BuyerCancellationCommand;
import com.uteexpress.order.dto.BuyerCancellationInput;
import com.uteexpress.order.service.OrderLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/buyer/orders/{orderId}/cancel")
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).USER.authority())")
public class BuyerCancellationController {
    private final OrderLifecycleService lifecycle;
    public BuyerCancellationController(OrderLifecycleService lifecycle) { this.lifecycle=lifecycle; }

    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> cancel(@PathVariable Long orderId, @Valid @RequestBody BuyerCancellationInput input,
            HttpServletRequest request) {
        // The version belongs exclusively to the JSON body; query actor/version fields cannot override it.
        if(request.getParameterMap().keySet().stream().anyMatch(key -> !key.equals("_csrf"))
                || request.getParameterMap().values().stream().anyMatch(values -> values.length!=1))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        lifecycle.cancelForBuyer(new BuyerCancellationCommand(orderId,input.expectedOrderVersion()));
        return Map.of("result","OK");
    }
}
