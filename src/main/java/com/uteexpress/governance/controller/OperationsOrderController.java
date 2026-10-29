package com.uteexpress.governance.controller;

import com.uteexpress.governance.dto.OpsOrderDetail;
import com.uteexpress.governance.dto.OpsOrderPage;
import com.uteexpress.governance.service.OperationsOrderService;
import com.uteexpress.governance.service.RoleGovernanceService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/{ops:admin|manager}/orders")
public class OperationsOrderController {
    private final OperationsOrderService orders;

    private final RoleGovernanceService roles;

    public OperationsOrderController(OperationsOrderService orders, RoleGovernanceService roles) {
        this.orders = orders;
        this.roles = roles;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public OpsOrderPage listJson(@RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return orders.search(query, status, page, size);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String list(@PathVariable String ops, @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Model model) {
        model.addAttribute("ops", ops);
        model.addAttribute("query", query);
        model.addAttribute("status", status);
        model.addAttribute("statuses", OperationsOrderService.STATUSES);
        model.addAttribute("orders", orders.search(query, status, page, size));
        return "governance/orders/list";
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public OpsOrderDetail detailJson(@PathVariable Long id) { return orders.detail(id); }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String detail(@PathVariable String ops, @PathVariable Long id, Model model) {
        model.addAttribute("ops", ops);
        var order = orders.detail(id);
        model.addAttribute("order", order);
        // Advisory UI only. Existing JSON commands recheck state, version and active role under locks.
        boolean ready = "CONFIRMED".equals(order.facts().status()) && order.readyAt() != null;
        boolean assign = ready && order.shipment() == null;
        boolean reassign = ready && order.shipment() != null
                && "ASSIGNED".equals(order.shipment().status()) && order.shipment().pickedUpAt() == null;
        if (assign || reassign) {
            var shippers = roles.activeShippers().stream()
                    .filter(shipper -> order.shipment() == null
                            || !Long.valueOf(shipper.id()).equals(order.shipment().assignedShipperId()))
                    .toList();
            model.addAttribute("assignmentShippers", shippers);
            model.addAttribute("assignmentAction", "/" + ops + "/orders/" + id + (assign ? "/assign" : "/reassign"));
            model.addAttribute("assignmentVersionField", assign ? "expectedOrderVersion" : "expectedShipmentVersion");
            model.addAttribute("assignmentVersion", assign ? order.version() : order.shipment().version());
            model.addAttribute("assignmentLabel", assign ? "Phân công Shipper" : "Phân công lại Shipper");
        }
        return "governance/orders/detail";
    }
}
