package com.appmgr.api.validation;

import com.appmgr.api.validation.impl.VersionTypeValidation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VersionTypeValidation.class)
@Documented
public @interface VersionType {
    boolean allowNull() default false;

    String message() default "Version type is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
