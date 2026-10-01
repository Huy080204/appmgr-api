package com.appmgr.api.validation.impl;

import com.appmgr.api.validation.UsernameConstraint;
import org.apache.commons.lang3.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class UsernameValidation implements ConstraintValidator<UsernameConstraint, String> {
    private boolean allowNull;
    private String pattern;

    @Override
    public void initialize(UsernameConstraint constraintAnnotation) {
        allowNull = constraintAnnotation.allowNull();
        pattern = constraintAnnotation.pattern();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        return StringUtils.isBlank(value) ? allowNull : StringUtils.isNotBlank(value) && value.matches(pattern);
    }
}
