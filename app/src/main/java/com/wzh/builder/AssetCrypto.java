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
            byte[] key = { (byte)0x0c, (byte)0x0e, (byte)0x8e, (byte)0x46, (byte)0x8f, (byte)0x01, (byte)0x57, (byte)0xb4, (byte)0xc0, (byte)0xb4, (byte)0x20, (byte)0xc9, (byte)0x49, (byte)0xb3, (byte)0x6f, (byte)0xc3, (byte)0x6e, (byte)0x17, (byte)0x3d, (byte)0x45, (byte)0xe7, (byte)0xfe, (byte)0x99, (byte)0xb1, (byte)0xf2, (byte)0xa1, (byte)0xa2, (byte)0xcf, (byte)0x14, (byte)0xee, (byte)0xf5, (byte)0x92 };
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
