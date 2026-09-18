package com.uteexpress.common.controller;

import com.uteexpress.common.dto.FoundationResponse;
import com.uteexpress.common.service.FoundationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foundation")
public class FoundationController {
    private final FoundationService foundationService;

    public FoundationController(FoundationService foundationService) {
        this.foundationService = foundationService;
    }

    @GetMapping
    public FoundationResponse describe() {
        return foundationService.describe();
    }
}
