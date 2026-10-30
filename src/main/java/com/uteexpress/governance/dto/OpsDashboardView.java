package com.uteexpress.governance.dto;

import java.util.List;

public record OpsDashboardView(String title, String description, String dashboardPath,
        List<Capability> capabilities) {
    public OpsDashboardView { capabilities = List.copyOf(capabilities); }
    public record Capability(String title, String description) { }
}
