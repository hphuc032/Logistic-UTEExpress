package com.uteexpress.order.controller;

import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.order.dto.PlaceOrderResult;
import com.uteexpress.order.service.OrderPlacementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/checkout")
public class PlaceOrderController {
    private final OrderPlacementService placement;
    public PlaceOrderController(OrderPlacementService placement) { this.placement = placement; }

    @PostMapping("/place-order")
    ResponseEntity<PlaceOrderResult> place(@Valid @RequestBody CheckoutRequest request) {
        var result = placement.placeOrder(request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
}
