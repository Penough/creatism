package com.creatism.cafelogger.core;

import com.creatism.cafelogger.properties.CafeLoggerProperties;

/**
 * Singleton Pattern for CafeLoggerConfuguration
 *
 * @author xuyu
 */
public class CafeLoggerConfugurationHolder {
    private static CafeLoggerProperties config;

    private CafeLoggerConfugurationHolder() {}

    public static CafeLoggerProperties getConfig() {
        return config;
    }

    public static void setConfig(CafeLoggerProperties properties) {
        if(config!=null) {
            throw new RuntimeException("CafeLogger configuration can only init once!");
        }
        config = properties;
    }
}
