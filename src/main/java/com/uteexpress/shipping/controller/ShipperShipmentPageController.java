package com.uteexpress.shipping.controller;

import com.uteexpress.shipping.service.ShipperAssignmentReadService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ShipperShipmentPageController {
    private final ShipperAssignmentReadService assignments;
    public ShipperShipmentPageController(ShipperAssignmentReadService assignments) {
        this.assignments = assignments;
    }
    @GetMapping("/shipper/shipments/view")
    public String list(Model model) {
        model.addAttribute("shipments", assignments.assigned());
        return "shipping/shipper/list";
    }
    @GetMapping("/shipper/shipments/{shipmentId}/view")
    public String detail(@PathVariable Long shipmentId, Model model) {
        model.addAttribute("shipment", assignments.detail(shipmentId));
        return "shipping/shipper/detail";
    }
}
