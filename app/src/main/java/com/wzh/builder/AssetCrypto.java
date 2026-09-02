package com.wzh.builder;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// Decrypts EmbeddedAssets' bundled site content (AES-256-CBC, one random
// IV per file prepended to that file's ciphertext). The key intentionally
// lives inside decrypt()'s method body -- see the comment in
// embedAssetsAsCode (server.js) for exactly why that placement matters
// once Dex2C hardening runs on this class.
final class AssetCrypto {
    static byte[] decrypt(byte[] ivAndCipherText) {
        try {
            byte[] key = { (byte)0x12, (byte)0x30, (byte)0x1d, (byte)0x2e, (byte)0x5b, (byte)0x42, (byte)0x06, (byte)0x93, (byte)0xe4, (byte)0x70, (byte)0x61, (byte)0xbc, (byte)0xd4, (byte)0xd4, (byte)0x14, (byte)0x60, (byte)0x8c, (byte)0xac, (byte)0xc6, (byte)0x7a, (byte)0x72, (byte)0x5b, (byte)0x78, (byte)0x03, (byte)0xe0, (byte)0x29, (byte)0xb8, (byte)0x21, (byte)0xd1, (byte)0x5c, (byte)0x66, (byte)0x40 };
            byte[] iv = new byte[16];
            System.arraycopy(ivAndCipherText, 0, iv, 0, 16);
            byte[] cipherText = new byte[ivAndCipherText.length - 16];
            System.arraycopy(ivAndCipherText, 16, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("Asset decrypt failed", e);
        }
    }

    private AssetCrypto() {}
}
