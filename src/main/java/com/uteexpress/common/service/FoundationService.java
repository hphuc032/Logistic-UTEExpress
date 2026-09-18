package com.uteexpress.common.service;

import com.uteexpress.common.dto.FoundationResponse;
import org.springframework.stereotype.Service;

@Service
public class FoundationService {
    public FoundationResponse describe() {
        return new FoundationResponse("UTEExpress", "FOUNDATION_READY");
    }
}
