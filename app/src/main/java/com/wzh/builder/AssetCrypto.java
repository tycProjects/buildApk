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
            byte[] key = { (byte)0xfe, (byte)0x38, (byte)0x53, (byte)0x6f, (byte)0x1a, (byte)0x5e, (byte)0x8a, (byte)0x04, (byte)0x15, (byte)0x47, (byte)0x80, (byte)0xa7, (byte)0x76, (byte)0x7b, (byte)0xe9, (byte)0x4d, (byte)0x6a, (byte)0xb6, (byte)0x5c, (byte)0xe3, (byte)0xcd, (byte)0xaa, (byte)0xf9, (byte)0xd0, (byte)0x1d, (byte)0x17, (byte)0xfe, (byte)0xf9, (byte)0x1d, (byte)0x7d, (byte)0x7b, (byte)0x71 };
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
