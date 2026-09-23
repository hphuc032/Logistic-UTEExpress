package com.uteexpress.governance;

import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.governance.service.OpsDashboardService;
import com.uteexpress.identity.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OpsDashboardTest {
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean RegistrationService registrationService;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoleRepository;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identityAuthenticationService;
    @MockitoBean AuditLogService auditLogService;
    @Autowired MockMvc mvc;
    @Autowired OpsDashboardService dashboards;

    @Test
    void guestCannotOpenEitherDashboard() throws Exception {
        for (String path : new String[]{"/admin/dashboard", "/manager/dashboard"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "VENDOR", "MANAGER", "SHIPPER"})
    void onlyAdminCanOpenAdminDashboard(String role) throws Exception {
        mvc.perform(get("/admin/dashboard").with(user("ops").roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "VENDOR", "ADMIN", "SHIPPER"})
    void onlyManagerCanOpenManagerDashboard(String role) throws Exception {
        mvc.perform(get("/manager/dashboard").with(user("ops").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPageUsesSharedLayoutAndHonestEmptyState() throws Exception {
        mvc.perform(get("/admin/dashboard").with(user("ops").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("governance/dashboard"))
                .andExpect(content().string(containsString("Quản trị hệ thống")))
                .andExpect(content().string(containsString("Nhật ký quản trị")))
                .andExpect(content().string(containsString("Chưa có dữ liệu tổng quan")))
                .andExpect(content().string(containsString("id=\"main-navigation\"")))
                .andExpect(content().string(containsString("id=\"site-footer\"")))
                .andExpect(content().string(not(containsString("href=\"/admin/audit-logs\""))))
                .andDo(result -> savePreview("admin", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void managerPageDoesNotOfferAdminOnlyCapabilities() throws Exception {
        mvc.perform(get("/manager/dashboard").with(user("ops").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(view().name("governance/dashboard"))
                .andExpect(content().string(containsString("Điều hành vận hành")))
                .andExpect(content().string(not(containsString("Nhật ký quản trị"))))
                .andExpect(content().string(not(containsString("Duyệt cửa hàng"))))
                .andDo(result -> savePreview("manager", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void directAdminServiceCallStillRejectsManager() {
        assertThatThrownBy(() -> dashboards.adminDashboard()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void directManagerServiceCallRequiresManagerRole() {
        assertThatThrownBy(() -> dashboards.managerDashboard()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dualRoleUserCanUseBothRoutes() throws Exception {
        for (String path : new String[]{"/admin/dashboard", "/manager/dashboard"}) {
            mvc.perform(get(path).with(user("ops").roles("ADMIN", "MANAGER")))
                    .andExpect(status().isOk());
        }
    }

    private static void savePreview(String role, String html) throws java.io.IOException {
        var directory = java.nio.file.Path.of("target", "ui-preview");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(role + ".html"), html);
    }
}
