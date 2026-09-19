package com.generated.jn21com;

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
            byte[] key = { (byte)0x27, (byte)0xcd, (byte)0x22, (byte)0x92, (byte)0x29, (byte)0x98, (byte)0xdb, (byte)0x75, (byte)0x66, (byte)0xdd, (byte)0x52, (byte)0x47, (byte)0x00, (byte)0x85, (byte)0x22, (byte)0x25, (byte)0x41, (byte)0x35, (byte)0xc8, (byte)0xac, (byte)0x8b, (byte)0x43, (byte)0xb3, (byte)0x0b, (byte)0x46, (byte)0xf5, (byte)0x1a, (byte)0x67, (byte)0x5a, (byte)0x82, (byte)0x8f, (byte)0x56 };
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
