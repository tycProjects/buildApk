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
            byte[] key = { (byte)0xcc, (byte)0xd8, (byte)0xf2, (byte)0x5c, (byte)0xad, (byte)0x6e, (byte)0x3f, (byte)0x2a, (byte)0xe1, (byte)0xe6, (byte)0x66, (byte)0x0d, (byte)0x16, (byte)0x0a, (byte)0x53, (byte)0xe6, (byte)0xf3, (byte)0x9d, (byte)0x44, (byte)0x18, (byte)0x39, (byte)0xcd, (byte)0x34, (byte)0xc6, (byte)0x0f, (byte)0x51, (byte)0x5c, (byte)0xce, (byte)0x78, (byte)0xae, (byte)0x23, (byte)0xba };
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
