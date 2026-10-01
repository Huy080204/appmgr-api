package com.appmgr.api.validation;

import com.appmgr.api.validation.impl.PhoneValidation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneValidation.class)
@Documented
public @interface PhoneConstraint {
    boolean allowNull() default false;
    String message() default "Phone must be a well-formed phone number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
