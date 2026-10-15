package com.uteexpress.fulfillment.controller;

import com.uteexpress.fulfillment.dto.FulfillmentRequest;
import com.uteexpress.fulfillment.dto.FulfillmentResult;
import com.uteexpress.fulfillment.service.ShipperFulfillmentService;
import com.uteexpress.order.dto.OrderAction;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shipper/orders/{orderId}")
public class ShipperFulfillmentController {
    private final ShipperFulfillmentService fulfillment;
    public ShipperFulfillmentController(ShipperFulfillmentService fulfillment) { this.fulfillment = fulfillment; }
    @PostMapping(value="/pickup", consumes="application/json")
    public FulfillmentResult pickup(@PathVariable Long orderId, @Valid @RequestBody FulfillmentRequest request) {
        return fulfillment.transition(orderId, OrderAction.PICK_UP, request);
    }
    @PostMapping(value="/shipping", consumes="application/json")
    public FulfillmentResult shipping(@PathVariable Long orderId, @Valid @RequestBody FulfillmentRequest request) {
        return fulfillment.transition(orderId, OrderAction.START_SHIPPING, request);
    }
    @PostMapping(value="/delivered", consumes="application/json")
    public FulfillmentResult delivered(@PathVariable Long orderId, @Valid @RequestBody FulfillmentRequest request) {
        return fulfillment.transition(orderId, OrderAction.DELIVER, request);
    }
}
