package com.appmgr.api.validation.impl;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.validation.VersionType;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class VersionTypeValidation implements ConstraintValidator<VersionType, Integer> {
    private boolean allowNull;

    @Override
    public void initialize(VersionType constraintAnnotation) {
        allowNull = constraintAnnotation.allowNull();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null) {
            return allowNull;
        }
        return BaseConstant.VERSION_TYPE_BUNDLE.equals(value)
                || BaseConstant.VERSION_TYPE_STORE.equals(value)
                || BaseConstant.VERSION_TYPE_OTA.equals(value);
    }
}
