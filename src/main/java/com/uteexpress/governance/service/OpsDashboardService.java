package com.uteexpress.governance.service;

import com.uteexpress.governance.dto.OpsDashboardView;
import com.uteexpress.governance.dto.OpsDashboardView.Capability;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpsDashboardService {
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
    public OpsDashboardView adminDashboard() {
        return new OpsDashboardView("Quản trị hệ thống",
                "Không gian quản lý tài khoản, quyền truy cập và chính sách của UTEExpress.",
                "/admin/dashboard", List.of(
                new Capability("Tài khoản và phân quyền", "Quản lý quyền truy cập và trạng thái tài khoản."),
                new Capability("Duyệt cửa hàng", "Xem xét hồ sơ đăng ký của nhà bán hàng."),
                new Capability("Danh mục và vận chuyển", "Quản lý danh mục, đơn vị vận chuyển và phí giao hàng."),
                new Capability("Nhật ký quản trị", "Theo dõi các thay đổi quản trị trên hệ thống.")));
    }

    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    public OpsDashboardView managerDashboard() {
        return new OpsDashboardView("Điều hành vận hành",
                "Không gian hỗ trợ khách hàng và phối hợp vận hành hằng ngày.",
                "/manager/dashboard", List.of(
                new Capability("Hỗ trợ khách hàng", "Tra cứu và hỗ trợ thông tin liên hệ của khách hàng."),
                new Capability("Theo dõi cửa hàng", "Theo dõi hoạt động của các cửa hàng đã được duyệt."),
                new Capability("Danh mục và vận chuyển", "Phối hợp quản lý danh mục và dịch vụ giao hàng.")));
    }
}
