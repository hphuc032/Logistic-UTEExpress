package com.uteexpress.fulfillment.controller;

import com.uteexpress.fulfillment.dto.FulfillmentRequest;
import com.uteexpress.fulfillment.service.ShipperFulfillmentService;
import com.uteexpress.order.dto.OrderAction;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/shipper/orders/{orderId}")
public class ShipperFulfillmentPageController {
    private final ShipperFulfillmentService fulfillment;
    public ShipperFulfillmentPageController(ShipperFulfillmentService fulfillment) { this.fulfillment = fulfillment; }
    @PostMapping(value="/{action:pickup|shipping|delivered}", consumes="application/x-www-form-urlencoded")
    public String transition(@PathVariable Long orderId, @PathVariable String action,
            @Valid @ModelAttribute FulfillmentRequest request) {
        OrderAction command = switch (action) {
            case "pickup" -> OrderAction.PICK_UP;
            case "shipping" -> OrderAction.START_SHIPPING;
            case "delivered" -> OrderAction.DELIVER;
            default -> throw new IllegalArgumentException("Unsupported fulfillment action");
        };
        fulfillment.transition(orderId, command, request);
        return "redirect:/shipper/shipments/view";
    }
}
