package com.todapk;

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
            byte[] key = { (byte)0xdc, (byte)0xc6, (byte)0xd8, (byte)0x9e, (byte)0x54, (byte)0x05, (byte)0x9b, (byte)0x77, (byte)0x0f, (byte)0x6f, (byte)0x4d, (byte)0x25, (byte)0x9b, (byte)0xa0, (byte)0x72, (byte)0x62, (byte)0xf1, (byte)0x14, (byte)0x3b, (byte)0xf9, (byte)0x96, (byte)0x22, (byte)0x20, (byte)0x62, (byte)0x18, (byte)0x60, (byte)0x07, (byte)0x6f, (byte)0x64, (byte)0x47, (byte)0x97, (byte)0x18 };
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
