package com.appmgr.api.validation.impl;


import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.validation.StatusConstraint;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Objects;

public class StatusValidation implements ConstraintValidator<StatusConstraint, Integer> {
    private boolean allowNull;

    @Override
    public void initialize(StatusConstraint constraintAnnotation) {
        allowNull = constraintAnnotation.allowNull();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null && allowNull) {
            return true;
        }
        return Objects.equals(value, BaseConstant.STATUS_ACTIVE)
                || Objects.equals(value, BaseConstant.STATUS_PENDING)
                || Objects.equals(value, BaseConstant.STATUS_LOCK);
    }
}