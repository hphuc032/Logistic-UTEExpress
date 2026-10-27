package com.uteexpress.order.controller;

import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.OrderLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/{ops:admin|manager}/orders/{id}/return-to-sender")
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
public class ReturnToSenderController extends ReturnControllerSupport {
    private final OrderLifecycleService lifecycle;
    public ReturnToSenderController(OrderLifecycleService lifecycle) { this.lifecycle = lifecycle; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    ReturnToSenderResult json(@PathVariable Long id, @Valid @RequestBody ReturnToSenderInput input, HttpServletRequest request) {
        parameters(request);
        return perform(id,input);
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String form(@PathVariable String ops, @PathVariable Long id, @Valid ReturnToSenderInput input, HttpServletRequest request) {
        parameters(request,"expectedOrderVersion","expectedShipmentVersion","receiptReference");
        perform(id,input);
        return "redirect:/" + ops + "/orders/" + id;
    }

    private ReturnToSenderResult perform(Long id, ReturnToSenderInput input) {
        return lifecycle.confirmReturnToSender(new ReturnToSenderCommand(id,input.expectedOrderVersion(),
                input.expectedShipmentVersion(),input.receiptReference()));
    }
}
