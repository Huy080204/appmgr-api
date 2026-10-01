package com.appmgr.api.utils;

import org.apache.commons.lang3.RandomStringUtils;

public class StringUtils {
    public static String generateRandomString(int length) {
        return RandomStringUtils.randomAlphanumeric(length).toLowerCase();
    }
}
