package com.uteexpress;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.RegistrationService;
import com.uteexpress.identity.repository.UserRoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(FoundationHttpTest.ErrorFixtureController.class)
class FoundationHttpTest {
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean
    private com.uteexpress.governance.service.AuditLogService auditLogService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private UserRoleRepository userRoleRepository;

    @MockitoBean
    private com.uteexpress.identity.service.IdentityAuthenticationService identityAuthenticationService;

    @Autowired
    private MockMvc mvc;

    @Test
    void applicationBootsAndReturnsFoundationDto() throws Exception {
        mvc.perform(get("/api/v1/foundation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("UTEExpress"))
                .andExpect(jsonPath("$.status").value("FOUNDATION_READY"));
    }

    @Test
    void healthIsUpWithoutDatabaseOrExternalServices() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void actuatorEnvironmentIsNotExposed() throws Exception {
        mvc.perform(get("/actuator/env").with(user("foundation-test"))).andExpect(status().isNotFound());
    }

    @Test
    void validRequestBindsToDto() throws Exception {
        mvc.perform(post("/test-fixtures/validation").with(user("foundation-test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Foundation\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Foundation"));
    }

    @Test
    void beanValidationUsesErrorContractWithoutRejectedValues() throws Exception {
        mvc.perform(post("/test-fixtures/validation").with(user("foundation-test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test-fixtures/validation"));
    }

    @Test
    void unknownFieldsAreRejectedInsteadOfMassAssigned() throws Exception {
        mvc.perform(post("/test-fixtures/validation").with(user("foundation-test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Foundation\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedJsonDoesNotLeakBody() throws Exception {
        mvc.perform(post("/test-fixtures/validation").with(user("foundation-test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("secret-not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("secret-not-json"))));
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        mvc.perform(post("/test-fixtures/validation").with(user("foundation-test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedContentTypePreserves415() throws Exception {
        mvc.perform(post("/test-fixtures/validation").with(user("foundation-test")).with(csrf())
                        .contentType(MediaType.TEXT_PLAIN).content("name=test"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void missingResourceUses404Contract() throws Exception {
        mvc.perform(get("/test-fixtures/missing").with(user("foundation-test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void businessConflictUses409Contract() throws Exception {
        mvc.perform(get("/test-fixtures/conflict").with(user("foundation-test")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void unknownRouteUses404Contract() throws Exception {
        mvc.perform(get("/does-not-exist").with(user("foundation-test")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void wrongHttpMethodPreserves405AndAllowHeader() throws Exception {
        mvc.perform(post("/api/v1/foundation").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalMessageOrQueryString() throws Exception {
        mvc.perform(get("/test-fixtures/unexpected?token=private-token").with(user("foundation-test")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.path").value("/test-fixtures/unexpected"))
                .andExpect(content().string(not(containsString("database-password"))))
                .andExpect(content().string(not(containsString("private-token"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    // Fixtures are test-only: no validation demo or error-trigger endpoints ship in the application.
    @RestController
    static class ErrorFixtureController {
        record TestRequest(@NotBlank String name) { }

        @PostMapping("/test-fixtures/validation")
        TestRequest validate(@Valid @RequestBody TestRequest request) {
            return request;
        }

        @GetMapping("/test-fixtures/missing")
        void missing() {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/test-fixtures/conflict")
        void conflict() {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }

        @GetMapping("/test-fixtures/unexpected")
        void unexpected() {
            throw new IllegalStateException("database-password must never reach a client");
        }
    }
}
