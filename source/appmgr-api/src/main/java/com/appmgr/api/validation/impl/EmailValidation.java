package com.appmgr.api.validation.impl;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.validation.EmailConstraint;
import org.apache.commons.lang3.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class EmailValidation implements ConstraintValidator<EmailConstraint, String> {
    private boolean allowNull;

    @Override
    public void initialize(EmailConstraint constraintAnnotation) {
        allowNull = constraintAnnotation.allowNull();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        return StringUtils.isBlank(value) ? allowNull : StringUtils.isNotBlank(value) && value.matches(BaseConstant.EMAIL_PATTERN);
    }
}
