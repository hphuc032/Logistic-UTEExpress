package com.uteexpress.identity.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

public class PasswordMatchesValidator
        implements ConstraintValidator<PasswordMatches, PasswordConfirmation> {
    @Override
    public boolean isValid(PasswordConfirmation form, ConstraintValidatorContext context) {
        if (form == null || Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("confirmPassword")
                .addConstraintViolation();
        return false;
    }
}
