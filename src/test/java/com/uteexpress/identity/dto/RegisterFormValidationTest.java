package com.uteexpress.identity.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterFormValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAValidRegistrationForm() {
        assertThat(validator.validate(form("user@example.com", "Phuc03", "securePass1", "securePass1")))
                .isEmpty();
    }

    @Test
    void rejectsBlankAndMalformedEmail() {
        assertThat(paths(form(" ", "Phuc03", "securePass1", "securePass1"))).contains("email");
        assertThat(paths(form("not-an-email", "Phuc03", "securePass1", "securePass1"))).contains("email");
    }

    @Test
    void rejectsUnsafeUsername() {
        assertThat(paths(form("user@example.com", "ab", "securePass1", "securePass1")))
                .contains("username");
        assertThat(paths(form("user@example.com", "name with space", "securePass1", "securePass1")))
                .contains("username");
    }

    @Test
    void rejectsShortMismatchedAndBcryptOversizedPasswords() {
        assertThat(paths(form("user@example.com", "Phuc03", "short", "short"))).contains("password");
        assertThat(paths(form("user@example.com", "Phuc03", "securePass1", "differentPass1")))
                .contains("confirmPassword");

        String utf8Oversized = "🙂".repeat(19);
        assertThat(utf8Oversized.length()).isLessThanOrEqualTo(64);
        assertThat(paths(form("user@example.com", "Phuc03", utf8Oversized, utf8Oversized)))
                .contains("password");
    }

    private java.util.Set<String> paths(RegisterForm form) {
        return validator.validate(form).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private RegisterForm form(String email, String username, String password, String confirmation) {
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setUsername(username);
        form.setPassword(password);
        form.setConfirmPassword(confirmation);
        return form;
    }
}
