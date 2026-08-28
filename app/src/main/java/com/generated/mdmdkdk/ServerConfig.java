package com.generated.mdmdkdk;

public final class ServerConfig {
    private ServerConfig() {}

    public static String getBaseUrl() {
        String[] p = new String[] {
            "https://",
            "zip-to-apk-ce53.",
            "onrender.com"
        };
        StringBuilder out = new StringBuilder();
        for (String part : p) out.append(part);
        return out.toString();
    }
}
