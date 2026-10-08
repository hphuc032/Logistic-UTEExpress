package com.uteexpress.shipping.controller;

import com.uteexpress.shipping.dto.AssignShipmentRequest;
import com.uteexpress.shipping.dto.ReassignShipmentRequest;
import com.uteexpress.shipping.dto.ShipmentAssignment;
import com.uteexpress.shipping.service.ShipmentAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/{ops:admin|manager}/orders/{orderId}")
public class ShipmentAssignmentController {
    private final ShipmentAssignmentService assignments;

    public ShipmentAssignmentController(ShipmentAssignmentService assignments) {
        this.assignments = assignments;
    }

    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentAssignment assign(@PathVariable Long orderId, @Valid @RequestBody AssignShipmentRequest request) {
        return assignments.assign(orderId, request.shipperId(), request.expectedOrderVersion());
    }

    @PostMapping("/reassign")
    public ShipmentAssignment reassign(@PathVariable Long orderId,
            @Valid @RequestBody ReassignShipmentRequest request) {
        return assignments.reassign(orderId, request.shipperId(), request.expectedShipmentVersion());
    }
}
