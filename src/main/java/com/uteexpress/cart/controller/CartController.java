package com.uteexpress.cart.controller;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.dto.CartView;
import com.uteexpress.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** JSON foundation endpoints; owner always comes from the service's authenticated principal provider. */
@RestController
@RequestMapping("/user/cart")
public class CartController {
    private final CartService carts;

    public CartController(CartService carts) { this.carts = carts; }

    @GetMapping
    public CartView current() {
        return carts.getCurrentUserCart().orElseGet(() -> new CartView(null, java.util.List.of(),
                java.math.BigDecimal.ZERO));
    }

    @PostMapping("/items")
    public CartView add(@Valid @RequestBody AddCartProductRequest request) {
        return carts.addProduct(request);
    }
}
