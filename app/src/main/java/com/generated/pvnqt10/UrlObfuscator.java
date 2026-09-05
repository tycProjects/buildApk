package com.generated.pvnqt10;

// Shared runtime reconstructor for URL literals hidden by
// protectHttpsLiteralsInJava (see server.js) -- every generated class in
// this package that had a hardcoded http(s):// string literal calls
// through here instead of holding the URL as a plain const-string, so a
// decompiled dex/smali dump doesn't show the plaintext URL directly on
// whichever class used it, only this shared XOR unscrambler plus a
// per-call int array and salt. Reverse-engineering hardening, same
// caveat as ServerConfig's own decode(): not encryption in the security
// sense, not a replacement for server-side authorization.
final class UrlObfuscator {
    private UrlObfuscator() {}

    static String decode(int[] data, int salt) {
        if (data == null || data.length == 0) return "";
        char[] out = new char[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (char) (data[i] ^ ((salt + i * 31) & 0xff));
        }
        return new String(out);
    }
}
