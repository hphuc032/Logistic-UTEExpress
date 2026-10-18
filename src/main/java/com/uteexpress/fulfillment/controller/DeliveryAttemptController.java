package com.uteexpress.fulfillment.controller;

import com.uteexpress.common.exception.*;
import com.uteexpress.fulfillment.dto.*;
import com.uteexpress.fulfillment.service.DeliveryAttemptCoordinator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/shipper/orders/{orderId}")
public class DeliveryAttemptController {
    private final DeliveryAttemptCoordinator coordinator;
    public DeliveryAttemptController(DeliveryAttemptCoordinator coordinator) { this.coordinator=coordinator; }
    @PostMapping(value="/delivery-failed",consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    public DeliveryAttemptResult failureJson(@PathVariable Long orderId, @Valid @RequestBody DeliveryFailureRequest input,
            HttpServletRequest request) {
        parameters(request,Set.of("_csrf")); return coordinator.reportFailure(orderId,input);
    }
    @PostMapping(value="/delivery-failed",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String failureForm(@PathVariable Long orderId, @Valid @ModelAttribute DeliveryFailureRequest input,
            HttpServletRequest request) {
        parameters(request,Set.of("_csrf","expectedOrderVersion","expectedShipmentVersion","failureReason","evidenceReference"));
        coordinator.reportFailure(orderId,input); return "redirect:/shipper/shipments/view";
    }
    @PostMapping(value="/retry",consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    public DeliveryAttemptResult retryJson(@PathVariable Long orderId, @Valid @RequestBody RetryDeliveryRequest input,
            HttpServletRequest request) {
        parameters(request,Set.of("_csrf")); return coordinator.retryDelivery(orderId,input);
    }
    @PostMapping(value="/retry",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String retryForm(@PathVariable Long orderId, @Valid @ModelAttribute RetryDeliveryRequest input,
            HttpServletRequest request) {
        parameters(request,Set.of("_csrf","expectedOrderVersion","expectedShipmentVersion"));
        coordinator.retryDelivery(orderId,input); return "redirect:/shipper/shipments/view";
    }
    private static void parameters(HttpServletRequest request, Set<String> allowed) {
        if(request.getParameterMap().entrySet().stream().anyMatch(e -> !allowed.contains(e.getKey()) || e.getValue().length!=1))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
    }
}
