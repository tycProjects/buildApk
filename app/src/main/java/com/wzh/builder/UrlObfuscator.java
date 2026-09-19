package com.wzh.builder;

// Shared runtime reconstructor for string literals hidden by
// protectJavaStringLiterals (see server.js) -- every generated class in
// this package that had a hardcoded string literal calls through here
// instead of holding it as a plain const-string, so a decompiled dex/smali
// dump doesn't show the plaintext directly on whichever class used it,
// only this shared XOR unscrambler plus a per-call int array and salt.
// (With the "String protection" toggle off, only http(s):// literals get
// this treatment; with it on, every eligible literal in the file does --
// see the 'all' flag on protectJavaStringLiterals.) Reverse-engineering
// hardening, same caveat as ServerConfig's own decode(): not encryption in
// the security sense, not a replacement for server-side authorization.
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
