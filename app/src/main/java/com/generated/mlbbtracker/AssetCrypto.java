package com.generated.mlbbtracker;

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
            byte[] key = { (byte)0x84, (byte)0xd6, (byte)0xbc, (byte)0x44, (byte)0x72, (byte)0x8b, (byte)0x31, (byte)0x9a, (byte)0x24, (byte)0xc1, (byte)0x60, (byte)0xca, (byte)0xab, (byte)0x18, (byte)0xfb, (byte)0xd2, (byte)0x85, (byte)0xd3, (byte)0xf0, (byte)0xc4, (byte)0x52, (byte)0x31, (byte)0xb8, (byte)0x08, (byte)0x1c, (byte)0x24, (byte)0x57, (byte)0x89, (byte)0xb6, (byte)0xa2, (byte)0xeb, (byte)0x08 };
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
