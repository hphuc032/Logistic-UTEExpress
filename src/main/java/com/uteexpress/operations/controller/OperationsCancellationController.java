package com.uteexpress.operations.controller;

import com.uteexpress.operations.dto.OpsCancellationInput;
import com.uteexpress.operations.service.OperationsCancellationService;
import com.uteexpress.order.dto.OrderStatusChangedEvent;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/{ops:admin|manager}/orders/{id}/cancel")
public class OperationsCancellationController {
    private final OperationsCancellationService cancellation;
    public OperationsCancellationController(OperationsCancellationService cancellation) { this.cancellation = cancellation; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public OrderStatusChangedEvent cancelJson(@PathVariable Long id, @Valid @RequestBody OpsCancellationInput input,
            HttpServletRequest request) {
        parameters(request, Set.of("_csrf"));
        return cancellation.cancel(id, input);
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String cancelForm(@PathVariable String ops, @PathVariable Long id,
            @Valid @ModelAttribute OpsCancellationInput input, HttpServletRequest request) {
        parameters(request, Set.of("_csrf", "expectedOrderStatus", "expectedOrderVersion",
                "expectedShipment", "expectedShipmentVersion", "reasonCode"));
        cancellation.cancel(id, input);
        return "redirect:/" + ops + "/orders/" + id;
    }
    private static void parameters(HttpServletRequest request, Set<String> allowed) {
        if (request.getParameterMap().entrySet().stream()
                .anyMatch(entry -> !allowed.contains(entry.getKey()) || entry.getValue().length != 1)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
    }
}
