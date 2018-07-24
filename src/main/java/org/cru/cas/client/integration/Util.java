package org.cru.cas.client.integration;

import javax.servlet.FilterConfig;

class Util {

    private Util() {}

    static void checkNotNullOrEmpty(String value, String message) {
        if (isNullOrEmpty(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    static boolean isNullOrEmpty(String prefix) {
        return prefix == null || prefix.trim().isEmpty();
    }

    static String getRequiredInitParam(FilterConfig filterConfig, String name) {
        String value = filterConfig.getInitParameter(name);
        checkNotNullOrEmpty(value, "required init-param " + name + " is not specified");
        return value;
    }

    static String nullToEmpty(String pathInfo) {
        if (pathInfo == null) {
            return "";
        } else {
            return pathInfo;
        }
    }
}
