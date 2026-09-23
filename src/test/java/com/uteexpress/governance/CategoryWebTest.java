package com.uteexpress.governance;

import com.uteexpress.governance.dto.*;
import com.uteexpress.governance.service.*;
import com.uteexpress.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class CategoryWebTest {
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    @MockitoBean CategoryService categories;
    @MockitoBean AuditLogService audit;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @Autowired MockMvc mvc;

    @Test void roleMatrixAndCsrfProtectMutations() throws Exception {
        for (String ops : List.of("admin", "manager")) {
            String path = "/" + ops + "/categories";
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : List.of("USER", "VENDOR", "SHIPPER", ops.equals("admin") ? "MANAGER" : "ADMIN")) {
                mvc.perform(get(path).with(user("actor").roles(role))).andExpect(status().isForbidden());
                mvc.perform(post(path).with(user("actor").roles(role)).with(csrf())
                        .param("name", "Books").param("slug", "books")).andExpect(status().isForbidden());
            }
            mvc.perform(post(path).with(user("actor").roles(ops.toUpperCase())))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(categories);
    }

    @Test void rendersEscapedListAndBothFormsWithCsrf() throws Exception {
        when(categories.list(0)).thenReturn(new PageImpl<>(List.of(
                new CategoryView(1L, "<script>alert(1)</script>", "books", true, 0L),
                new CategoryView(2L, "Hidden", "hidden", false, 1L)), PageRequest.of(0,20), 2));
        when(categories.get(1L)).thenReturn(new CategoryView(1L,"Books","books",true,0L));
        for (String ops : List.of("admin", "manager")) {
            String path = "/" + ops + "/categories";
            mvc.perform(get(path).with(user("actor").roles(ops.toUpperCase())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("&lt;script&gt;")))
                    .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                    .andExpect(content().string(containsString(path + "/1/disable")))
                    .andExpect(content().string(containsString(path + "/2/enable")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andDo(r -> preview(ops + "-list", r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
            mvc.perform(get(path + "/new").with(user("actor").roles(ops.toUpperCase())))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
            mvc.perform(get(path + "/1/edit").with(user("actor").roles(ops.toUpperCase())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(path + "/1/update")))
                    .andExpect(content().string(containsString("name=\"version\"")))
                    .andDo(r -> preview(ops + "-edit", r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }
    @Test void invalidInputRendersErrorsWithoutServiceCall() throws Exception {
        mvc.perform(post("/admin/categories").with(user("actor").roles("ADMIN")).with(csrf())
                .param("name", " ").param("slug", "../BAD"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("categoryRequest","name","slug"))
                .andExpect(view().name("governance/categories/form"));
        verifyNoInteractions(categories);
    }
    @Test void createIgnoresClientActorAndActiveFieldsAndRedirects() throws Exception {
        mvc.perform(post("/manager/categories").with(user("actor").roles("MANAGER")).with(csrf())
                .param("name", " Books ").param("slug", "books").param("actorId","99").param("active","false"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/manager/categories"));
        verify(categories).create(new CategoryRequest("Books","books",null));
    }
    @Test void duplicateAndStaleEditsShowSafeFeedback() throws Exception {
        when(categories.create(any())).thenThrow(new ApplicationException(ErrorCode.CONFLICT));
        mvc.perform(post("/admin/categories").with(user("actor").roles("ADMIN")).with(csrf())
                .param("name","Books").param("slug","books"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("categoryRequest","slug"));
        when(categories.update(eq(1L),any())).thenThrow(new ApplicationException(ErrorCode.CONFLICT));
        mvc.perform(post("/admin/categories/1/update").with(user("actor").roles("ADMIN")).with(csrf())
                .param("name","Books").param("slug","books").param("version","0"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("/admin/categories/1/edit")));
    }
    @Test void visibilityActionsUsePostedVersionAndFixedAction() throws Exception {
        mvc.perform(post("/admin/categories/1/disable").with(user("actor").roles("ADMIN")).with(csrf())
                .param("version","2")).andExpect(redirectedUrl("/admin/categories"));
        verify(categories).setActive(1L,2L,false);
        mvc.perform(post("/manager/categories/1/enable").with(user("actor").roles("MANAGER")).with(csrf())
                .param("version","3")).andExpect(redirectedUrl("/manager/categories"));
        verify(categories).setActive(1L,3L,true);
    }
    private void preview(String name, String html) throws Exception {
        var path = java.nio.file.Path.of("target","ui-preview","category-" + name + ".html");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path,html);
    }
}
