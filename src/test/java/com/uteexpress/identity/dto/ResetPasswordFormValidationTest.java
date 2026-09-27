package com.uteexpress.identity.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordFormValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsSamePasswordPolicyAsRegistration() {
        assertThat(validator.validate(form(
                "user@example.com", "123456", "securePass1", "securePass1"))).isEmpty();
    }

    @Test
    void rejectsInvalidEmailOtpAndPasswordConfirmation() {
        assertThat(paths(form("not-email", "12x", "short", "differentPass1")))
                .contains("email", "code", "newPassword", "confirmPassword");
    }

    @Test
    void rejectsPasswordAboveBcryptUtf8Limit() {
        String oversized = "🙂".repeat(19);
        assertThat(paths(form("user@example.com", "123456", oversized, oversized)))
                .contains("newPassword");
    }

    private Set<String> paths(ResetPasswordForm form) {
        return validator.validate(form).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private ResetPasswordForm form(String email, String code,
            String password, String confirmation) {
        ResetPasswordForm form = new ResetPasswordForm();
        form.setEmail(email);
        form.setCode(code);
        form.setNewPassword(password);
        form.setConfirmPassword(confirmation);
        return form;
    }
}
