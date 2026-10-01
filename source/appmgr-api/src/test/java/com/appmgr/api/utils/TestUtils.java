package com.appmgr.api.utils;

import com.appmgr.api.jwt.BaseJwt;

public final class TestUtils {

    private TestUtils() {
    }

    public static BaseJwt superAdminJwt() {
        BaseJwt jwt = new BaseJwt();
        jwt.setIsSuperAdmin(true);
        return jwt;
    }

    public static BaseJwt superAdminJwt(long accountId) {
        BaseJwt jwt = new BaseJwt();
        jwt.setAccountId(accountId);
        jwt.setIsSuperAdmin(true);
        return jwt;
    }

    public static BaseJwt notSuperAdminJwt() {
        BaseJwt jwt = new BaseJwt();
        jwt.setIsSuperAdmin(false);
        return jwt;
    }

    public static BaseJwt jwtWithUserKind(Integer userKind) {
        BaseJwt jwt = new BaseJwt();
        jwt.setUserKind(userKind);
        return jwt;
    }
}
