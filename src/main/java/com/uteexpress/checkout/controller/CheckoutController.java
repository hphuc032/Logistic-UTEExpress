package com.uteexpress.checkout.controller;

import com.uteexpress.checkout.dto.CheckoutPreview;
import com.uteexpress.checkout.dto.QuoteRequest;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/checkout")
public class CheckoutController {
    private final CheckoutQuoteService checkout;
    public CheckoutController(CheckoutQuoteService checkout) { this.checkout = checkout; }

    @PostMapping("/quote")
    CheckoutPreview quote(@Valid @RequestBody QuoteRequest request) { return checkout.quote(request); }
}
