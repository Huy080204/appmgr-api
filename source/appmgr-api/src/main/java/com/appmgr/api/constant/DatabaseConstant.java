package com.appmgr.api.constant;

public class DatabaseConstant {
    public static final String PREFIX_TABLE = "db_";
    public static final String APP_ID_GENERATOR_STRATEGY = "com.appmgr.api.service.id.IdGenerator";
    public static final String APP_ID_GENERATOR_NAME = "idGenerator";

    private DatabaseConstant() {
        throw new IllegalStateException("Utility class");
    }
}
