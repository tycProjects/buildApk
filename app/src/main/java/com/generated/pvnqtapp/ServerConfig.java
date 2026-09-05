package com.generated.pvnqtapp;

public final class ServerConfig {
    private ServerConfig() {}

    private static String decode(int[] data, int salt) {
        if (data == null || data.length == 0) return "";
        char[] out = new char[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (char) (data[i] ^ ((salt + i * 31) & 0xff));
        }
        return new String(out);
    }

    // Exact Dex2C target: the app's remote WebView start URL.
    public static String getBaseUrl() {
        return decode(new int[] { 197, 184, 159, 122, 90, 114, 72, 169, 198, 171, 141, 118, 68, 46, 43, 81, 178, 206, 174, 212, 99, 92, 50, 0, 230, 154, 169, 147, 99, 83, 39, 7, 251, 201, 185, 196, 108, 80, 51, 3, 247, 202, 162, 142, 46, 83, 75, 49, 15, 253, 220, 191, 214, 125, 90, 35, 25, 245, 199, 183, 149, 63, 31, 97, 41, 227, 220, 164, 133, 103, 70, 34, 74, 244, 213, 172, 207, 104, 107, 83, 49 }, 173);
    }

    // Exact Dex2C target: the update API server URL.
    public static String getUpdateServerUrl() {
        return decode(new int[] { 51, 14, 237, 200, 164, 204, 58, 27, 41, 27, 225, 157, 187, 129, 32, 77, 59, 1, 164, 203, 162, 211, 54, 10, 44, 12, 243, 197, 209, 186, 152, 110, 21, 57, 22, 245 }, 91);
    }
}
