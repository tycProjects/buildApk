package com.egg.raz8;

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
            byte[] key = { (byte)0x55, (byte)0x54, (byte)0x07, (byte)0x04, (byte)0x3a, (byte)0xde, (byte)0x75, (byte)0xa1, (byte)0x29, (byte)0x81, (byte)0x2c, (byte)0xbb, (byte)0xdb, (byte)0xbd, (byte)0x28, (byte)0xd5, (byte)0x48, (byte)0x7b, (byte)0xa2, (byte)0x3c, (byte)0x07, (byte)0x0a, (byte)0xcc, (byte)0xd4, (byte)0x9e, (byte)0xdf, (byte)0x30, (byte)0x49, (byte)0x98, (byte)0xa8, (byte)0x3c, (byte)0x1f };
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
