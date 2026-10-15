package com.uteexpress.shipping.controller;

import com.uteexpress.shipping.dto.ShipmentAssignment;
import com.uteexpress.shipping.service.ShipperAssignmentReadService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shipper/shipments")
public class ShipperAssignmentController {
    private final ShipperAssignmentReadService assignments;

    public ShipperAssignmentController(ShipperAssignmentReadService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    public List<ShipmentAssignment> assigned() { return assignments.assigned(); }

    @GetMapping("/{shipmentId}")
    public ShipmentAssignment detail(@PathVariable Long shipmentId) {
        return assignments.detail(shipmentId);
    }
}
