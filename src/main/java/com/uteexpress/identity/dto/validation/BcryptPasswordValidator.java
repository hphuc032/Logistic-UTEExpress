package com.uteexpress.identity.dto.validation;

import com.uteexpress.identity.validation.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BcryptPasswordValidator implements ConstraintValidator<ValidBcryptPassword, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PasswordPolicy.isWithinBcryptLimit(value);
    }
}
