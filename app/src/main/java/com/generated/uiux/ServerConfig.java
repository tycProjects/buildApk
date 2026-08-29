package com.generated.uiux;

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
        return decode(new int[] { 197, 184, 159, 122, 90, 114, 72, 169, 214, 173, 155, 47, 68, 57, 58, 80, 243, 217, 175, 150, 112, 94, 46, 88, 244, 196, 163, 221, 50, 31, 44, 1, 233, 201, 228, 153, 38, 76, 35, 86, 231, 197, 247, 209, 57, 13, 92, 63, 24, 250, 150, 238, 192, 121, 1, 123, 20, 165, 133, 183, 220, 37, 29, 123, 11, 187, 157, 243, 143, 49, 17, 115, 0 }, 173);
    }

    // Exact Dex2C target: the update API server URL.
    public static String getUpdateServerUrl() {
        return decode(new int[] { 51, 14, 237, 200, 164, 204, 58, 27, 41, 27, 225, 157, 187, 129, 32, 77, 59, 1, 164, 203, 162, 211, 54, 10, 44, 12, 243, 197, 209, 186, 152, 110, 21, 57, 22, 245 }, 91);
    }
}
