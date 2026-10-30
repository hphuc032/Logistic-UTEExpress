package com.uteexpress.identity.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = BcryptPasswordValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBcryptPassword {
    String message() default "Mật khẩu vượt quá giới hạn 72 byte của BCrypt.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
