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
            byte[] key = { (byte)0x64, (byte)0xdc, (byte)0x98, (byte)0x3b, (byte)0x82, (byte)0x7b, (byte)0xd9, (byte)0xed, (byte)0xde, (byte)0xac, (byte)0x14, (byte)0xdd, (byte)0x2f, (byte)0xc4, (byte)0xe3, (byte)0xe7, (byte)0xb3, (byte)0x45, (byte)0xd4, (byte)0xfd, (byte)0xd8, (byte)0xed, (byte)0xab, (byte)0x0d, (byte)0xc5, (byte)0x38, (byte)0xc9, (byte)0x42, (byte)0x6e, (byte)0x8a, (byte)0xe8, (byte)0x03 };
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
