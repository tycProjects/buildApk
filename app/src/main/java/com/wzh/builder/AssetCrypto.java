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
            byte[] key = { (byte)0x51, (byte)0x5b, (byte)0x62, (byte)0x6a, (byte)0xe1, (byte)0x30, (byte)0x74, (byte)0xf1, (byte)0xac, (byte)0x46, (byte)0x38, (byte)0x3c, (byte)0xa2, (byte)0x10, (byte)0x66, (byte)0xb8, (byte)0x5e, (byte)0xe3, (byte)0x94, (byte)0xe5, (byte)0xaf, (byte)0x47, (byte)0x71, (byte)0x2b, (byte)0xad, (byte)0x7a, (byte)0x85, (byte)0x2a, (byte)0xf7, (byte)0x1b, (byte)0x2c, (byte)0x5e };
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
